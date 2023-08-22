// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.credentials

/**
 * Credentials for services where the access token has a limited expiration time
 *
 * @property refreshToken token for updating the access token
 * @property expiresIn access token expiration time
 */
interface CredentialsWithRefresh : Credentials {
  val refreshToken: String
  val expiresIn: Long

  /**
   * Checking the expiration of the token
   */
  fun isAccessTokenValid(): Boolean
}
