// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import org.jetbrains.annotations.Nls
import java.net.Authenticator
import java.net.ProxySelector

internal object PlatformJdkProxyProvider {
  val proxySelector: ProxySelector = IdeProxySelector(asProxyConfigurationProvider(ProxySettings::getInstance))
  val authenticator: Authenticator = IdeProxyAuthenticator(asProxyAuthentication(ProxyAuthentication::getInstance))
}

private fun asProxyConfigurationProvider(getProxySettings: () -> ProxySettings): ProxyConfigurationProvider =
  ProxyConfigurationProvider { getProxySettings().getProxyConfiguration() }

private fun asProxyAuthentication(getProxyAuthentication: () -> ProxyAuthentication): ProxyAuthentication = object : ProxyAuthentication {
  override fun getKnownAuthentication(host: String, port: Int): Credentials? =
    getProxyAuthentication().getKnownAuthentication(host, port)

  override fun getPromptedAuthentication(prompt: @Nls String, host: String, port: Int): Credentials? =
    getProxyAuthentication().getPromptedAuthentication(prompt, host, port)

  override fun isPromptedAuthenticationCancelled(host: String, port: Int): Boolean =
    getProxyAuthentication().isPromptedAuthenticationCancelled(host, port)

  override fun enablePromptedAuthentication(host: String, port: Int) =
    getProxyAuthentication().enablePromptedAuthentication(host, port)
}