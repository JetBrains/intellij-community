// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION")
package com.intellij.util.net.internal

import com.intellij.credentialStore.Credentials
import com.intellij.util.net.DisabledProxyAuthPromptsManager
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.ProxyCredentialProvider
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.proxy.CommonProxy
import com.intellij.util.text.nullize
import java.net.PasswordAuthentication

private fun HttpConfigurable.getCredentials(): Credentials? {
  // as in com.intellij.util.net.HttpConfigurable.getPromptedAuthentication
  if (!PROXY_AUTHENTICATION) {
    return null
  }
  val login = proxyLogin
  if (login.isNullOrEmpty()) {
    return null
  }
  val password = plainProxyPassword?.nullize()
  return Credentials(login, password?.toCharArray())
}

private fun HttpConfigurable.setCredentials(credentials: Credentials?) {
  if (credentials == null) {
    PROXY_AUTHENTICATION = false
    proxyLogin = null
    plainProxyPassword = null
  }
  else {
    PROXY_AUTHENTICATION = true
    proxyLogin = credentials.userName
    plainProxyPassword = credentials.password?.toString()
  }
}

private fun PasswordAuthentication.toCredentials(): Credentials = Credentials(userName, password)

internal fun (() -> HttpConfigurable).asProxyCredentialStore(): ProxyCredentialStore = HttpConfigurableToCredentialStoreAdapter(this)
internal fun (() -> HttpConfigurable).asDisabledProxyAuthPromptsManager(): DisabledProxyAuthPromptsManager = HttpConfigurableToDisabledPromptsManager(this)

private class HttpConfigurableToCredentialStoreAdapter(private val getHttpConfigurable: () -> HttpConfigurable) : ProxyCredentialStore, ProxyCredentialProvider {
  private val httpConfigurable: HttpConfigurable get() = getHttpConfigurable()

  // host is not checked in com.intellij.util.net.HttpConfigurable.getPromptedAuthentication, but here we check it
  // theoretically might change the behavior, but shouldn't be critical

  @Synchronized
  override fun getCredentials(host: String, port: Int): Credentials? = when {
    httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port -> httpConfigurable.getCredentials()
    else -> httpConfigurable.getGenericPassword(host, port)?.toCredentials()
  }

  @Synchronized
  override fun setCredentials(host: String, port: Int, credentials: Credentials?, remember: Boolean) {
    if (httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port) {
      httpConfigurable.setCredentials(credentials)
      httpConfigurable.KEEP_PROXY_PASSWORD = credentials != null && remember
    }
    else if (credentials == null || credentials.password == null) {
      httpConfigurable.removeGeneric(CommonProxy.HostInfo(null, host, port))
    }
    else {
      httpConfigurable.putGenericPassword(host, port, PasswordAuthentication(credentials.userName, credentials.password!!.toCharArray()), remember)
    }
  }

  @Synchronized
  override fun areCredentialsRemembered(host: String, port: Int): Boolean = when {
    httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port -> httpConfigurable.KEEP_PROXY_PASSWORD
    else -> httpConfigurable.isGenericPasswordRemembered(host, port)
  }

  @Synchronized
  override fun clearTransientCredentials() {
    httpConfigurable.clearGenericPasswords()
  }

  @Synchronized
  override fun clearAllCredentials() {
    httpConfigurable.setCredentials(null)
    httpConfigurable.plainProxyPassword = null
    httpConfigurable.KEEP_PROXY_PASSWORD = false
    httpConfigurable.clearGenericPasswords()
  }
}

private class HttpConfigurableToDisabledPromptsManager(private val getHttpConfigurable: () -> HttpConfigurable) : DisabledProxyAuthPromptsManager {
  private val httpConfigurable get() = getHttpConfigurable()

  @Synchronized
  override fun disablePromptedAuthentication(host: String, port: Int) {
    if (httpConfigurable.USE_HTTP_PROXY && httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port) {
      httpConfigurable.AUTHENTICATION_CANCELLED = true
    }
    else {
      httpConfigurable.setGenericPasswordCanceled(host, port)
    }
  }

  @Synchronized
  override fun isPromptedAuthenticationDisabled(host: String, port: Int): Boolean {
    return if (httpConfigurable.USE_HTTP_PROXY && httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port) {
      httpConfigurable.AUTHENTICATION_CANCELLED
    }
    else {
      httpConfigurable.isGenericPasswordCanceled(host, port)
    }
  }

  @Synchronized
  override fun enablePromptedAuthentication(host: String, port: Int) {
    if (httpConfigurable.USE_HTTP_PROXY && httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port) {
      httpConfigurable.AUTHENTICATION_CANCELLED = false
    }
    else {
      httpConfigurable.removeGenericPasswordCancellation(host, port)
    }
  }

  @Synchronized
  override fun enableAllPromptedAuthentications() {
    httpConfigurable.AUTHENTICATION_CANCELLED = false
    httpConfigurable.clearGenericCancellations()
  }
}
