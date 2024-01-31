// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.util.Url

interface OAuthRequest<T : Credentials> {
  /**
   * Url that is used by OAuth flow to redirection when user accepted the OAuth request
   */
  val authorizationCodeUrl: Url

  /**
   * Used to exchange code for credentials
   */
  val credentialsAcquirer: OAuthCredentialsAcquirer<T>

  /**
   * Url that is usually opened in browser where user can accept authorization
   * if PKCE is supported generated hash of code verifier
   */
  val authUrlWithParameters: Url
}