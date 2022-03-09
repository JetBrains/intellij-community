// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

/**
 * Helper which inject Authorization header to all requests
 */
abstract class HttpRequestAuthorizationConfigurer : HttpRequestConfigurerBase() {

  override val commonHeaders: Map<String, String>
    get() = super.commonHeaders +
            ("Authorization" to getAuthorizationHeaderValue())

  protected abstract fun getAuthorizationHeaderValue(): String
}