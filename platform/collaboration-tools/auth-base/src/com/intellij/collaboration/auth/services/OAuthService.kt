// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import java.util.concurrent.CompletableFuture

/**
 * General interface for authorization services
 *
 * @param T Service credentials, must implement the Credentials interface
 *
 * @property name name of the service
 */
interface OAuthService<T : Credentials> {
  val name: String

  /**
   * Starting the authorization flow
   */
  fun authorize(request: OAuthRequest<T>): CompletableFuture<T>

  /**
   * Revoking the access token
   */
  fun revokeToken(token: String)

  /**
   * Exchanging code for credentials
   */
  @Deprecated("Use handleOAuthServerCallback instead", ReplaceWith("handleOAuthServerCallback"))
  fun handleServerCallback(path: String, parameters: Map<String, List<String>>): Boolean {
    throw UnsupportedOperationException()
  }

  fun handleOAuthServerCallback(path: String, parameters: Map<String, List<String>>): OAuthResult<T>? {
    val isAccepted = handleServerCallback(path, parameters)
    return OAuthResult(null, isAccepted)
  }

  fun handleOAuthServerCredentialsCallback(path: String, parameters: Map<String, List<String>>): OAuthResultCredentials<T>? {
    handleServerCallback(path, parameters)
    return OAuthResultCredentials(null, Result.failure(UnsupportedOperationException()))
  }

  class OAuthResult<T : Credentials>(
    val request: OAuthRequest<T>?,
    val isAccepted: Boolean
  )

  class OAuthResultCredentials<T : Credentials>(
    val request: OAuthRequest<T>?,
    val callbackAnswer: Result<Credentials>
  ) {
    fun isAccepted(): Boolean = callbackAnswer.isSuccess
  }
}
