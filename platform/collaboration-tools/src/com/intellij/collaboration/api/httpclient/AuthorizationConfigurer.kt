// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.util.io.HttpSecurityUtil
import java.net.http.HttpRequest

/**
 * Injects Authorization header
 */
abstract class AuthorizationConfigurer : HttpRequestConfigurer {

  protected abstract val authorizationHeaderValue: String

  final override fun configure(builder: HttpRequest.Builder): HttpRequest.Builder = builder
    .header(HttpSecurityUtil.AUTHORIZATION_HEADER_NAME, authorizationHeaderValue)
}