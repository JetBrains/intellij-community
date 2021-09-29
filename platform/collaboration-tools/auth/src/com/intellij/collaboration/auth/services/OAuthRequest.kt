// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.util.Url

interface OAuthRequest {
  /**
   * Url that is used by OAuth flow to redirection when user accepted the OAuth request
   */
  val authorizationCodeUrl: Url

  /**
   * Url that is usually opened in browser where user can accept authorization
   * if PKCE is supported generated hash of code verifier
   */
  fun getAuthUrlWithParameters(): Url

  /**
   * Url that is going to be used to exchange code received from redirect to token (usually by https request)
   * if PKCE is supported url should contain code verifier whose hash was used in [getAuthUrlWithParameters]
   */
  fun getTokenUrlWithParameters(code: String): Url
}