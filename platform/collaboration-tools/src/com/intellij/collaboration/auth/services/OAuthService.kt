// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.util.Url
import java.util.concurrent.CompletableFuture

/**
 * General interface for authorization services
 *
 * @param T Service credentials, must implement the Credentials interface
 *
 * @property name name of the service
 * @property authorizationCodeUrl URL for getting the exchange code
 * @property successRedirectUrl redirect URL when authorization is completed successfully
 * @property errorRedirectUrl redirect URL when authorization is failed
 */
interface OAuthService<T : Credentials> {
  val name: String
  val authorizationCodeUrl: Url
  val successRedirectUrl: Url
  val errorRedirectUrl: Url

  /**
   * Starting the authorization flow
   */
  fun authorize(): CompletableFuture<T>

  /**
   * Revoking the access token
   */
  fun revokeToken(token: String)

  /**
   * Exchanging code for credentials
   */
  fun acceptCode(code: String): Boolean
}
