// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.CredentialsWithRefresh
import com.intellij.util.Url
import java.util.concurrent.CompletableFuture

/**
 * Authorization service with the ability to update an expired access token
 *
 * @param T Service credentials, must implement the CredentialsWithRefresh interface
 */
interface OAuthServiceWithRefresh<T: CredentialsWithRefresh>: OAuthService<T> {
  fun updateAccessToken(refreshTokenRequest: RefreshTokenRequest): CompletableFuture<T>

  interface RefreshTokenRequest {
    val refreshToken: String
    val refreshTokenUrlWithParameters: Url
  }
}
