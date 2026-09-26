// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.proxy

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import java.net.Authenticator
import java.net.ProxySelector
import kotlin.concurrent.Volatile

@ApiStatus.Internal
@Suppress("DEPRECATION")
object CommonProxyCompatibility {
  @JvmField
  @Volatile internal var mainProxySelector: ProxySelector? = null
  @JvmField
  @Volatile internal var mainAuthenticator: Authenticator? = null
  @JvmField
  @Volatile internal var registerCustomProxySelector: ((ProxySelector) -> AccessToken)? = null
  @JvmField
  @Volatile internal var registerCustomAuthenticator: ((Authenticator) -> AccessToken)? = null

  @Synchronized
  fun register(proxySelector: ProxySelector,
               authenticator: Authenticator,
               registerCustomProxySelector: (ProxySelector) -> AccessToken,
               registerCustomAuthenticator: (Authenticator) -> AccessToken) {
    if (mainProxySelector != null) {
      if (mainProxySelector === proxySelector && mainAuthenticator === authenticator) {
        if (ApplicationManager.getApplication()?.isUnitTestMode == true) {
          return
        }
        CommonProxy.LOG.warn("multiple registration of main proxy selector",
                             RuntimeException("current main proxy selector=$mainProxySelector, registration=$proxySelector"))
      } else {
        CommonProxy.LOG.error("multiple registration of main proxy selector",
                              RuntimeException("current main proxy selector=$mainProxySelector, registration=$proxySelector"))
      }
    }
    else {
      mainProxySelector = proxySelector
      mainAuthenticator = authenticator
      this.registerCustomProxySelector = registerCustomProxySelector
      this.registerCustomAuthenticator = registerCustomAuthenticator
    }
  }
}