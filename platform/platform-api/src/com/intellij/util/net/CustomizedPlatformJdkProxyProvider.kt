// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.application.AccessToken
import java.io.IOException
import java.net.*

internal object CustomizedPlatformJdkProxyProvider : JdkProxyProvider, JdkProxyCustomizer {
  override val originalProxySelector: ProxySelector get() = PlatformJdkProxyProvider.proxySelector
  override val originalAuthenticator: Authenticator get() = PlatformJdkProxyProvider.authenticator

  override val proxySelector: ProxySelector = CustomizedProxySelector()
  override val authenticator: Authenticator = CustomizedAuthenticator()

  @Volatile
  private var proxySelectors: List<ProxySelector> = emptyList()

  @Volatile
  private var authenticators: List<Authenticator> = emptyList()

  override fun customizeProxySelector(proxySelector: ProxySelector): AccessToken {
    synchronized(this) {
      proxySelectors = proxySelectors + proxySelector
    }
    return object : AccessToken() {
      override fun finish() {
        synchronized(this@CustomizedPlatformJdkProxyProvider) {
          proxySelectors = proxySelectors - proxySelector
        }
      }
    }
  }

  override fun customizeAuthenticator(authenticator: Authenticator): AccessToken {
    synchronized(this) {
      authenticators = authenticators + authenticator
    }
    return object : AccessToken() {
      override fun finish() {
        synchronized(this@CustomizedPlatformJdkProxyProvider) {
          authenticators = authenticators - authenticator
        }
      }
    }
  }

  private class CustomizedProxySelector : ProxySelector() {
    private val selectorReenterDefence: ThreadLocal<Boolean> = ThreadLocal()

    override fun select(uri: URI?): List<Proxy> {
      if (selectorReenterDefence.get() == true) {
        return NO_PROXY_LIST
      }
      selectorReenterDefence.set(true)
      try {
        for (proxySelector in proxySelectors) {
          val result = proxySelector.select(uri)
          if (!result.isNullOrEmpty()) return result
        }
        return originalProxySelector.select(uri)
      }
      finally {
        selectorReenterDefence.set(false)
      }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
      for (proxySelector in proxySelectors) {
        proxySelector.connectFailed(uri, sa, ioe)
      }
      originalProxySelector.connectFailed(uri, sa, ioe)
    }
  }

  private class CustomizedAuthenticator : Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
      for (authenticator in authenticators) {
        val creds = getPasswordAuthenticationUsing(authenticator)
        if (creds != null) {
          return creds
        }
      }
      return getPasswordAuthenticationUsing(originalAuthenticator)
    }

    private fun getPasswordAuthenticationUsing(authenticator: Authenticator): PasswordAuthentication? {
      return authenticator.requestPasswordAuthenticationInstance(
        requestingHost, requestingSite, requestingPort, requestingProtocol,
        requestingPrompt, requestingScheme, requestingURL, requestorType,
      )
    }
  }
}
