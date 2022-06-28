// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

class CredentialsAuthorizationConfigurer<Cred>(
  var credentials: Cred,
  private val headerValueExtractor: (Cred) -> String
) : AuthorizationConfigurer() {

  override val authorizationHeaderValue: String
    get() = headerValueExtractor(credentials)
}