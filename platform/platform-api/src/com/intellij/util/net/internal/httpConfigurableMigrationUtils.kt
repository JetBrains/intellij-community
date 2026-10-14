// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION")
package com.intellij.util.net.internal

import com.intellij.credentialStore.Credentials
import com.intellij.util.net.DisabledProxyAuthPromptsManager
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.ProxyCredentialStore

internal fun getPlatformProxyCredentialStore(): ProxyCredentialStore = TransitionalProxyCredentialStore()
internal fun getPlatformDisabledProxyAuthPromptsManager(): DisabledProxyAuthPromptsManager = HttpConfigurableBackedDisabledPromptsManager()

private class TransitionalProxyCredentialStore : ProxyCredentialStore {
  private val passwordSafeStore = PasswordSafeProxyCredentialStore()
  private val httpConfigurable get() = HttpConfigurable.getInstance()

  override fun getCredentials(host: String, port: Int): Credentials? {
    // migrating legacy credentials once
    if (httpConfigurable.KEEP_PROXY_PASSWORD && httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port) {
      val credentials = httpConfigurable.readCredentials()
      passwordSafeStore.setCredentials(host, port, credentials, remember = true)
      httpConfigurable.writeCredentials(null)
      httpConfigurable.KEEP_PROXY_PASSWORD = false
      return credentials
    }
    return passwordSafeStore.getCredentials(host, port)
  }

  override fun setCredentials(host: String, port: Int, credentials: Credentials?, remember: Boolean) {
    passwordSafeStore.setCredentials(host, port, credentials, remember)
    // for the unlikely case of `setCredentials` without prior `getCredentials`
    if (httpConfigurable.KEEP_PROXY_PASSWORD && httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port) {
      httpConfigurable.writeCredentials(null)
      httpConfigurable.KEEP_PROXY_PASSWORD = false
    }
  }

  override fun areCredentialsRemembered(host: String, port: Int): Boolean = passwordSafeStore.areCredentialsRemembered(host, port)

  override fun clearAllCredentials() {
    passwordSafeStore.clearAllCredentials()
    httpConfigurable.writeCredentials(null)
    httpConfigurable.KEEP_PROXY_PASSWORD = false
    httpConfigurable.clearGenericCancellations()
  }
}

private class HttpConfigurableBackedDisabledPromptsManager : DisabledProxyAuthPromptsManager {
  private val httpConfigurable get() = HttpConfigurable.getInstance()

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
