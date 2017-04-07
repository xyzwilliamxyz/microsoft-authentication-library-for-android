//  Copyright (c) Microsoft Corporation.
//  All rights reserved.
//
//  This code is licensed under the MIT License.
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files(the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions :
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.

package com.microsoft.identity.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entry point for developer to create the public native application, and make API call to get token.
 * MSAL {@link PublicClientApplication} provides three constructors, developer can choose to set client id in manifest
 * metadata or using constructor. If developer chooses to not use the default authority, it could also be set via constructor
 * or manifest metadata.
 */
public final class PublicClientApplication {
    private static final String TAG = PublicClientApplication.class.getSimpleName();

    private static final String CLIENT_ID_META_DATA = "com.microsoft.identity.client.ClientId";
    private static final String AUTHORITY_META_DATA = "com.microsoft.identity.client.Authority";
    private static final String INTERNET_PERMISSION = "android.permission.INTERNET";
    private static final String ACCESS_NETWORK_STATE_PERMISSION = "android.permission.ACCESS_NETWORK_STATE";
    private static final String DEFAULT_AUTHORITY = "https://login.microsoftonline.com/common/";

    private final Context mAppContext;
    private final TokenCache mTokenCache;

    private String mAuthorityString;
    private String mClientId;
    private String mComponent;
    private String mRedirectUri;

    private boolean mValidateAuthority = true;

    /**
     * {@link PublicClientApplication#PublicClientApplication(Context)} will read the client id (which must be set) from manifest, and if authority
     * is not set, default authority(https://login.microsoftonline.com/common) will be used.
     * <p>
     *      Client id <b>MUST</b> be set in the manifest as the meta data({@link IllegalArgumentException} will be thrown
     *      if client id is not provided), name for client id in the metadata is: "com.microsoft.identity.client.ClientId"
     *      Redirect uri <b>MUST</b> be set in the manifest as the meta data({@link IllegalArgumentException} will be thrown
     *      if client id is not provided), name for redirect uri in metadata is: "com.microsoft.identity.client.RedirectUri"
     *      Authority can be set in the meta data, if not provided, the sdk will use the default authority.
     * </p>
     *
     * @param context Application's {@link Context}. The sdk requires the application context to be passed in
     *                {@link PublicClientApplication}. Cannot be null. @note: The {@link Context} should be the application
     *                context instead of an running activity's context, which could potentially make the sdk hold a strong reference on
     *                the activity, thus preventing correct garbage collection and causing bugs.
     */
    public PublicClientApplication(@NonNull final Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is null.");
        }

        mAppContext = context;
        loadMetaDataFromManifest();
        mTokenCache = new TokenCache(mAppContext);

        initializeApplication();
    }

    /**
     * Developer can choose {@link PublicClientApplication#PublicClientApplication(Context, String)} to provide client id instead of
     * providing client id through metadata. If this constructor is called, default authority(https://login.microsoftonline.com/common)
     * will be used.
     * @param context Application's {@link Context}. The sdk requires the application context to be passed in
     *                {@link PublicClientApplication}. Cannot be null. @note: The {@link Context} should be the application
     *                context instead of an running activity's context, which could potentially make the sdk hold a strong reference on
     *                the activity, thus preventing correct garbage collection and causing bugs.
     * @param clientId The application client id.
     */
    public PublicClientApplication(@NonNull final Context context, @NonNull final String clientId) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }

        if (MSALUtils.isEmpty(clientId)) {
            throw new IllegalArgumentException("client id is empty or null");
        }

        mAppContext = context;
        mTokenCache = new TokenCache(mAppContext);
        mClientId = clientId;
        mAuthorityString = DEFAULT_AUTHORITY;

        initializeApplication();
    }

    /**
     * Developer can choose {@link PublicClientApplication#PublicClientApplication(Context, String, String)} to provide client id and authority instead of
     * providing them through metadata.
     *
     * @param context Application's {@link Context}. The sdk requires the application context to be passed in
     *                {@link PublicClientApplication}. Cannot be null. @note: The {@link Context} should be the application
     *                context instead of an running activity's context, which could potentially make the sdk hold a strong reference on
     *                the activity, thus preventing correct garbage collection and causing bugs.
     * @param clientId The application client id.
     * @param authority The default authority to be used for the authority.
     */
    public PublicClientApplication(@NonNull final Context context, @NonNull final String clientId, @NonNull final String authority) {
        this(context, clientId);

        if (MSALUtils.isEmpty(authority)) {
            throw new IllegalArgumentException("authority is empty or null");
        }

        mAuthorityString = authority;
    }

    private void initializeApplication() {
        mRedirectUri = createRedirectUri(mClientId);
        validateInputParameters();

        // Since network request is sent from the sdk, if calling app doesn't declare the internet permission in the
        // manifest, we cannot make the network call.
        checkInternetPermission();
        Logger.info(TAG, null, "Create new public client application.");
    }


    /**
     * @return The current version for the sdk.
     */
    public static String getSdkVersion() {
        return "1.0.0";
    }

    /**
     * Authority validation is turned on by default. If developer wants to turn off the authority validation, use the
     * {@link PublicClientApplication#setValidateAuthority(boolean)} to set it as false.
     * @param validateAuthority True if to turn on authority validation, false otherwise. (By default, authority
     *                          validation is turned on.)
     */
    public void setValidateAuthority(final boolean validateAuthority) {
        mValidateAuthority = validateAuthority;
    }

    /**
     * App developer can specify the string identifier to identify the component that consumes MSAL.
     * This is intended for libraries that consume MSAL that are embedded in apps that might also be using MSAL
     * as well, so for logging or telemetry app or library developers will be able to differentiate MSAL usage
     * by the app from MSAL usage by component libraries.
     * @param component The component identifier string passed into MSAL when creating the application object
     */
    public void setComponent(final String component) {
        mComponent = component;
    }

    /**
     * Returns the list of signed in users for the application.
     * @return Immutable List of all the signed in users.
     * @throws MsalClientException If failed to retrieve users from the cache.
     */
    public List<User> getUsers() throws MsalClientException {
        return mTokenCache.getUsers(Authority.createAuthority(mAuthorityString, mValidateAuthority).getAuthorityHost() , mClientId);
    }

    /**
     * The sdk requires calling app to pass in the {@link Activity} which <b> MUST </b> call this method to get the auth
     * code handled back correctly.
     * @param requestCode The request code for interactive request.
     * @param resultCode The result code for the request to get auth code.
     * @param data {@link Intent} either contains the url with auth code as query string or the errors.
     */
    public void handleInteractiveRequestRedirect(int requestCode, int resultCode, final Intent data) {
        InteractiveRequest.onActivityResult(requestCode, resultCode, data);
    }

    // Interactive APIs. Will launch the web UI.
    /**
     * Acquire token interactively, will pop-up webUI. Interactive flow will skip the cache lookup.
     * Default value for {@link UIBehavior} is {@link UIBehavior#SELECT_ACCOUNT}.
     * @param activity Non-null {@link Activity} that will be used as the parent activity for launching the {@link AuthenticationActivity}.
     *                 All the apps doing interactive request are required to call the
     *                 {@link PublicClientApplication#handleInteractiveRequestRedirect(int, int, Intent)} within the calling
     *                 activity {@link Activity#onActivityResult(int, int, Intent)}.
     * @param scopes An Non-null array of scopes to acquire token for.
     * @param callback The {@link AuthenticationCallback} to receive the result back.
     *                 1) If user cancels the flow by pressing the device back button, the result will be sent
     *                 back via {@link AuthenticationCallback#onCancel()}.
     *                 2) If the sdk successfully receives the token back, result will be sent back via
     *                 {@link AuthenticationCallback#onSuccess(AuthenticationResult)}
     *                 3) All the other errors will be sent back via
     *                 {@link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireToken(@NonNull final Activity activity, @NonNull final String[] scopes, @NonNull final AuthenticationCallback callback) {
        acquireTokenInteractive(activity, scopes, "", UIBehavior.SELECT_ACCOUNT, "", null, "", null, callback);
    }

    /**
     * Acquire token interactively, will pop-up webUI. Interactive flow will skip the cache lookup.
     * Default value for {@link UIBehavior} is {@link UIBehavior#SELECT_ACCOUNT}.
     * @param activity Non-null {@link Activity} that will be used as the parent activity for launching the {@link AuthenticationActivity}.
     *                 All the apps doing interactive request are required to call the
     *                 {@link PublicClientApplication#handleInteractiveRequestRedirect(int, int, Intent)} within the calling
     *                 activity {@link Activity#onActivityResult(int, int, Intent)}.
     * @param scopes An Non-null array of scopes to acquire the token for.
     * @param loginHint Optional. If provided, will be used as the query parameter sent for authenticating the user,
     *                  which will have the UPN pre-populated.
     * @param callback The Non-null {@link AuthenticationCallback} to receive the result back.
     *                 1) If user cancels the flow by pressing the device back button, the result will be sent
     *                 back via {@link AuthenticationCallback#onCancel()}.
     *                 2) If the sdk successfully receives the token back, result will be sent back via
     *                 {@link AuthenticationCallback#onSuccess(AuthenticationResult)}
     *                 3) All the other errors will be sent back via
     *                 {@link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireToken(@NonNull final Activity activity, final String[] scopes, final String loginHint,
                             @NonNull final AuthenticationCallback callback) {
        acquireTokenInteractive(activity, scopes, loginHint, UIBehavior.SELECT_ACCOUNT, "", null, "", null, callback);
    }

    /**
     * Acquire token interactively, will pop-up webUI. Interactive flow will skip the cache lookup.
     * Default value for {@link UIBehavior} is {@link UIBehavior#SELECT_ACCOUNT}.
     * @param activity Non-null {@link Activity} that will be used as the parent activity for launching the {@link AuthenticationActivity}.
     *                 All the apps doing interactive request are required to call the
     *                 {@link PublicClientApplication#handleInteractiveRequestRedirect(int, int, Intent)} within the calling
     *                 activity {@link Activity#onActivityResult(int, int, Intent)}.
     * @param scopes An Non-null array of scopes to acquire the token for.
     * @param loginHint Optional. If provided, will be used as the query parameter sent for authenticating the user,
     *                  which will have the UPN pre-populated.
     * @param uiBehavior The {@link UIBehavior} for prompting behavior. By default, the sdk use {@link UIBehavior#SELECT_ACCOUNT}.
     * @param extraQueryParameter Optional. The extra query parameter sent to authorize endpoint.
     * @param callback The Non-null {@link AuthenticationCallback} to receive the result back.
     *                 1) If user cancels the flow by pressing the device back button, the result will be sent
     *                 back via {@link AuthenticationCallback#onCancel()}.
     *                 2) If the sdk successfully receives the token back, result will be sent back via
     *                 {@link AuthenticationCallback#onSuccess(AuthenticationResult)}
     *                 3) All the other errors will be sent back via
     *                 {@link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireToken(@NonNull final Activity activity, @NonNull final String[] scopes, final String loginHint, final UIBehavior uiBehavior,
                             final String extraQueryParameter, @NonNull final AuthenticationCallback callback) {
        acquireTokenInteractive(activity, scopes, loginHint, uiBehavior, extraQueryParameter, null, "", null, callback);
    }

    /**
     * Acquire token interactively, will pop-up webUI. Interactive flow will skip the cache lookup.
     * Default value for {@link UIBehavior} is {@link UIBehavior#SELECT_ACCOUNT}.
     * @param activity Non-null {@link Activity} that will be used as the parent activity for launching the {@link AuthenticationActivity}.
     *                 All the apps doing interactive request are required to call the
     *                 {@link PublicClientApplication#handleInteractiveRequestRedirect(int, int, Intent)} within the calling
     *                 activity {@link Activity#onActivityResult(int, int, Intent)}.
     * @param scopes An Non-null array of scopes to acquire the token for.
     * @param user Optional. If provided, will be used to force the session continuation, if user tries to sign in with a different user,
     *             error will be returned.
     * @param uiBehavior The {@link UIBehavior} for prompting behavior. By default, the sdk use {@link UIBehavior#SELECT_ACCOUNT}.
     * @param extraQueryParameter Optional. The extra query parameter sent to authorize endpoint.
     * @param callback The Non-null {@link AuthenticationCallback} to receive the result back.
     *                 1) If user cancels the flow by pressing the device back button, the result will be sent
     *                 back via {@link AuthenticationCallback#onCancel()}.
     *                 2) If the sdk successfully receives the token back, result will be sent back via
     *                 {@link AuthenticationCallback#onSuccess(AuthenticationResult)}
     *                 3) All the other errors will be sent back via
     *                 {@link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireToken(@NonNull final Activity activity, @NonNull final String[] scopes, final User user, final UIBehavior uiBehavior,
                             final String extraQueryParameter, @NonNull final AuthenticationCallback callback) {
        acquireTokenInteractive(activity, scopes, "", uiBehavior, extraQueryParameter, null, "", user, callback);
    }

    /**
     * Acquire token interactively, will pop-up webUI. Interactive flow will skip the cache lookup.
     * Default value for {@link UIBehavior} is {@link UIBehavior#SELECT_ACCOUNT}.
     * @param activity Non-null {@link Activity} that will be used as the parent activity for launching the {@link AuthenticationActivity}.
     *                 All the apps doing interactive request are required to call the
     *                 {@link PublicClientApplication#handleInteractiveRequestRedirect(int, int, Intent)} within the calling
     *                 activity {@link Activity#onActivityResult(int, int, Intent)}.
     * @param scopes An Non-null array of scopes to acquire the token for.
     * @param loginHint Optional. If provided, will be used as the query parameter sent for authenticating the user,
     *                  which will have the UPN pre-populated.
     * @param uiBehavior The {@link UIBehavior} for prompting behavior. By default, the sdk use {@link UIBehavior#SELECT_ACCOUNT}.
     * @param extraQueryParams Optional. The extra query parameter sent to authorize endpoint.
     * @param additionalScope Optional. The additional scope to consent for.
     * @param authority Should be set if developer wants to get token for a different authority url.
     * @param callback The Non-null {@link AuthenticationCallback} to receive the result back.
     *                 1) If user cancels the flow by pressing the device back button, the result will be sent
     *                 back via {@link AuthenticationCallback#onCancel()}.
     *                 2) If the sdk successfully receives the token back, result will be sent back via
     *                 {@link AuthenticationCallback#onSuccess(AuthenticationResult)}
     *                 3) All the other errors will be sent back via
     *                 {@link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireToken(@NonNull final Activity activity, @NonNull final String[] scopes, final String loginHint, final UIBehavior uiBehavior,
                             final String extraQueryParams, final String[] additionalScope, final String authority,
                             @NonNull final AuthenticationCallback callback) {
        acquireTokenInteractive(activity, scopes, loginHint, uiBehavior == null ? UIBehavior.SELECT_ACCOUNT : uiBehavior,
                extraQueryParams, additionalScope, authority, null, callback);
    }

    /**
     * Acquire token interactively, will pop-up webUI. Interactive flow will skip the cache lookup.
     * Default value for {@link UIBehavior} is {@link UIBehavior#SELECT_ACCOUNT}.
     * @param activity Non-null {@link Activity} that will be used as the parent activity for launching the {@link AuthenticationActivity}.
     *                 All the apps doing interactive request are required to call the
     *                 {@link PublicClientApplication#handleInteractiveRequestRedirect(int, int, Intent)} within the calling
     *                 activity {@link Activity#onActivityResult(int, int, Intent)}.
     * @param scopes An Non-null array of scopes to acquire the token for.
     * @param user Optional. If provided, will be used to force the session continuation, if user tries to sign in with a different user, error
     *             will be returned.
     * @param uiBehavior The {@link UIBehavior} for prompting behavior. By default, the sdk use {@link UIBehavior#SELECT_ACCOUNT}.
     * @param extraQueryParams Optional. The extra query parameter sent to authorize endpoint.
     * @param additionalScope Optional. The additional scope to consent for.
     * @param authority Should be set if developer wants to get token for a different authority url.
     * @param callback The Non-null {@link AuthenticationCallback} to receive the result back.
     *                 1) If user cancels the flow by pressing the device back button, the result will be sent
     *                 back via {@link AuthenticationCallback#onCancel()}.
     *                 2) If the sdk successfully receives the token back, result will be sent back via
     *                 {@link AuthenticationCallback#onSuccess(AuthenticationResult)}
     *                 3) All the other errors will be sent back via
     *                 {@link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireToken(@NonNull final Activity activity, @NonNull final String[] scopes, final User user, final UIBehavior uiBehavior,
                             final String extraQueryParams, final String[] additionalScope, final String authority,
                             @NonNull final AuthenticationCallback callback) {
        acquireTokenInteractive(activity, scopes, "", uiBehavior == null ? UIBehavior.SELECT_ACCOUNT : uiBehavior, extraQueryParams, additionalScope,
                authority, user, callback);
    }

    // Silent call APIs.
    /**
     * Perform acquire token silent call. If there is a valid AT in the cache, the sdk will return the silent AT; If
     * no valid AT exists, the sdk will try to find a RT and use the RT to get a new access token. If RT does not exist
     * or it fails to use RT for a new AT, exception will be sent back via callback.
     * @param scopes The Non-null array of scopes to silently get the token for.
     * @param user {@link User} represents the user to silently be signed in.
     * @param callback {@link AuthenticationCallback} that is used to send the result back. The success result will be
     *                                               sent back via {@link AuthenticationCallback#onSuccess(AuthenticationResult)}.
     *                                               Failure case will be sent back via {
     *                                               @link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireTokenSilentAsync(@NonNull final String[] scopes, @NonNull final User user,
                                        @NonNull final AuthenticationCallback callback) {
        acquireTokenSilent(scopes, user, "", false, callback);
    }

    /**
     * Perform acquire token silent call. If there is a valid AT in the cache, the sdk will return the silent AT; If
     * no valid AT exists, the sdk will try to find a RT and use the RT to get a new access token. If RT does not exist
     * or it fails to use RT for a new AT, exception will be sent back via callback.
     * @param scopes The Non-null array of scopes to silently get the token for.
     * @param user {@link User} represents the user to silently be signed in.
     * @param authority (Optional) The alternate authority to get the token for. If not set, will use the default authority.
     * @param forceRefresh True if the request is forced to refresh, false otherwise.
     * @param callback {@link AuthenticationCallback} that is used to send the result back. The success result will be
     *                                               sent back via {@link AuthenticationCallback#onSuccess(AuthenticationResult)}.
     *                                               Failure case will be sent back via {
     *                                               @link AuthenticationCallback#onError(MsalException)}.
     */
    public void acquireTokenSilentAsync(@NonNull final String[] scopes, @NonNull final User user, final String authority,
                                        final boolean forceRefresh,
                                        @NonNull final AuthenticationCallback callback) {
        acquireTokenSilent(scopes, user, authority, forceRefresh, callback);
    }

    /**
     * Deletes all matching tokens (AT & RT) for the supplied {@link User} instance from the application cache.
     * @param user the {@link User} whose tokens should be deleted.
     */
    public void remove(final User user) {
        mTokenCache.deleteRefreshTokenByUser(user);
        mTokenCache.deleteAccessTokenByUser(user);
    }

    /**
     * Keep this method internal only to make it easy for MS apps to do serialize/deserialize on the family tokens.
     * @return The {@link TokenCache} that is used to persist token items for the running app.
     */
    TokenCache getTokenCache() {
        return mTokenCache;
    }

    private void loadMetaDataFromManifest() {
        final ApplicationInfo applicationInfo = MSALUtils.getApplicationInfo(mAppContext);
        if (applicationInfo == null || applicationInfo.metaData == null) {
            throw new IllegalArgumentException("No meta-data exists");
        }

        // read authority from manifest.
        final String authority = applicationInfo.metaData.getString(AUTHORITY_META_DATA);
        if (!MSALUtils.isEmpty(authority)) {
            mAuthorityString = authority;
        } else {
            mAuthorityString = DEFAULT_AUTHORITY;
        }

        // read client id from manifest
        final String clientId = applicationInfo.metaData.getString(CLIENT_ID_META_DATA);
        if (MSALUtils.isEmpty(clientId)) {
            throw new IllegalArgumentException("client id missing from manifest");
        }
        mClientId = clientId;

        // TODO: Comment out for now. As discussed, redirect should be computed during runtime, developer needs to put
//        final String redirectUri = applicationInfo.metaData.getString(REDIRECT_META_DATA);
//        if (!MSALUtils.isEmpty(redirectUri)) {
//            mRedirectUri = redirectUri;
//        }
    }

    // TODO: if no more input validation is needed, this could be moved back to the constructor.
    private void validateInputParameters() {
        if (!MSALUtils.hasCustomTabRedirectActivity(mAppContext, mRedirectUri)) {
            throw new IllegalStateException("App doesn't have the correct configuration for "
                    + BrowserTabActivity.class.getSimpleName() + ".");
        }
    }

    private void checkInternetPermission() {
        final PackageManager packageManager = mAppContext.getPackageManager();
        if (packageManager.checkPermission(INTERNET_PERMISSION, mAppContext.getPackageName())
                != PackageManager.PERMISSION_GRANTED
                || packageManager.checkPermission(ACCESS_NETWORK_STATE_PERMISSION, mAppContext.getPackageName())
                != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("android.permission.Internet or android.permission.ACCESS_NETWORK_STATE is missing");
        }
    }

    /**
     * Redirect uri will the in the format of msauth-clientid://appPackageName.
     * The sdk will comupte the redirect when the PublicClientApplication is initialized.
     */
    private String createRedirectUri(final String clientId) {
        return "msauth-" + clientId + "://" + mAppContext.getPackageName();
    }


    private void acquireTokenInteractive(final Activity activity, final String[] scopes, final String loginHint, final UIBehavior uiBehavior,
                                         final String extraQueryParams, final String[] additionalScope,
                                         final String authority, final User user, final AuthenticationCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is null");
        }

        final AuthenticationRequestParameters requestParameters = getRequestParameters(authority, scopes, loginHint,
                extraQueryParams, uiBehavior, user);

        Logger.info(TAG, requestParameters.getRequestContext(), "Preparing a new interactive request");
        final BaseRequest request = new InteractiveRequest(activity, requestParameters, additionalScope);
        request.getToken(callback);
    }

    private void acquireTokenSilent(final String[] scopes, final User user, final String authority,
                                    final boolean forceRefresh,
                                    final AuthenticationCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is null");
        }

        final Authority authorityForRequest = MSALUtils.isEmpty(authority) ? Authority.createAuthority(mAuthorityString, mValidateAuthority)
                : Authority.createAuthority(authority, mValidateAuthority);
        // set correlation if not developer didn't set it.
        final RequestContext requestContext = new RequestContext(UUID.randomUUID(), mComponent);
        final Set<String> scopesAsSet = new HashSet<>(Arrays.asList(scopes));
        final AuthenticationRequestParameters requestParameters = AuthenticationRequestParameters.create(authorityForRequest, mTokenCache,
                scopesAsSet, mClientId, requestContext);

        Logger.info(TAG, requestContext, "Preparing a new silent request");
        final BaseRequest request = new SilentRequest(mAppContext, requestParameters, forceRefresh, user);
        request.getToken(callback);
    }

    private AuthenticationRequestParameters getRequestParameters(final String authority, final String[] scopes,
                                                                 final String loginHint, final String extraQueryParam,
                                                                 final UIBehavior uiBehavior, final User user) {
        final Authority authorityForRequest = MSALUtils.isEmpty(authority) ? Authority.createAuthority(mAuthorityString, mValidateAuthority)
                : Authority.createAuthority(authority, mValidateAuthority);
        // set correlation if not developer didn't set it.
        final UUID correlationId = UUID.randomUUID();
        final Set<String> scopesAsSet = new HashSet<>(Arrays.asList(scopes));

        return AuthenticationRequestParameters.create(authorityForRequest, mTokenCache, scopesAsSet, mClientId,
                mRedirectUri, loginHint, extraQueryParam, uiBehavior, user, new RequestContext(correlationId, mComponent));
    }
}