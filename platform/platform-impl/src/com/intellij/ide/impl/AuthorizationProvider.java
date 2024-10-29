// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** @noinspection DeprecatedIsStillUsed*/
@ApiStatus.Internal
public interface AuthorizationProvider {
  String AUTH_TOKEN_HEADER_NAME = "Authorization";

  ExtensionPointName<AuthorizationProvider> EP_NAME = new ExtensionPointName<>("com.intellij.authorizationProvider");

  record TokenData(
    @NotNull Map<String, String> requestHeaders,
    @Nullable String idToken,
    @Nullable String refreshToken,
    @Nullable String exchangeUrl,
    @Nullable String clientId,
    long expiresInSec) {
  }
  /**
   * @deprecated use more generic method {@link #getRequestHeaders(String)} to implement authorization scheme consisting of one or more header entries
   * This method is called from a background thread in order to obtain an authorization access token for the given resource URL.
   * The access token has to be valid for at least 10 seconds. The implementation should not throw an exception, it is supposed to return a valid authentication token value or null if there was any error or
   * there is no access token obtained for the given resource.
   * The implementation is not expected to initiate an interactive authorization process and react on `Thread#interrupt`
   *
   * @param url the URL denoting the resource for which the access token is required
   * @return an access token data or null if the token does not exist. If the token exists, it should always be returned, even if it might be already expired.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  default String getAccessToken(@NotNull String url) {
    return null;
  }

  /**
   * @deprecated use more generic method {@link #getRequestHeaders(String, TokenData, boolean)} to implement authorization scheme consisting of one or more header entries
   * This method is called from a background thread in order to obtain authorization token (or a combination of tokens) that are to be used as authorization data in request's headers for the given resource URL.
   * The returned data has to be valid for at least 10 seconds. The implementation should not throw an exception, it is supposed to return a valid authentication token values or null if there was any error or
   * there is no authorization data obtained for the given resource.
   * The implementation is not expected to initiate an interactive authorization process and react on `Thread#interrupt`
   * Note: in order to remain compatible with the 'single-token' authorization scheme, the returned map must contain at least one entry with the key named like defined in the constant {@link #AUTH_TOKEN_HEADER_NAME}
   *
   * @param url the URL denoting the resource for which the authorization data should be used
   * @return an access token data or null if the token does not exist. If the token exists, it should always be returned, even if it might be already expired.
   */
  @Deprecated
  @Nullable
  default Map<String, String> getRequestHeaders(@NotNull String url) {
    String accessToken = getAccessToken(url);
    return accessToken != null? Map.of(AUTH_TOKEN_HEADER_NAME, accessToken) : null;
  }

  /**
   * This method is called from a background thread in order to obtain authorization token (or a combination of tokens) that are to be used as authorization data in request's headers for the given resource URL.
   * The returned data has to be valid for at least 10 seconds. The implementation should not throw an exception, it is supposed to return a valid authentication token values or null if there was any error or
   * there is no authorization data obtained for the given resource.
   * The implementation is not expected to initiate an interactive authorization process and react on `Thread#interrupt`
   * Note: in order to remain compatible with the 'single-token' authorization scheme, the returned map must contain at least one entry with the key named like defined in the constant {@link #AUTH_TOKEN_HEADER_NAME}
   *
   * @param token            current token data, that IDE currently has. Can be null.
   * @param url              the URL denoting the resource for which the authorization data should be used
   * @param isRefreshAttempt a hint flag. The 'true' value means that the current token is likely to be expired and token refresh can be performed to update it
   * @return an access token data or null if the token does not exist or the currentToken cannot be replaced/converted with a data from the provider. If some token exists, it should always be returned, even if it might be already expired.
   */
  @Nullable
  default Map<String, String> getRequestHeaders(@NotNull String url, @Nullable TokenData currentToken, boolean isRefreshAttempt) {
    return getRequestHeaders(url);
  }

}
