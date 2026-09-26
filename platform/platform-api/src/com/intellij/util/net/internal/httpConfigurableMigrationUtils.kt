// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION")
package com.intellij.util.net.internal

import com.intellij.credentialStore.Credentials
import com.intellij.util.net.DisabledProxyAuthPromptsManager
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.proxy.CommonProxy
import java.net.PasswordAuthentication
import java.nio.ByteBuffer

private fun PasswordAuthentication.toCredentials(): Credentials = Credentials(userName, password)

internal fun (() -> HttpConfigurable).asProxyCredentialStore(): ProxyCredentialStore = HttpConfigurableToCredentialStoreAdapter(this)
internal fun (() -> HttpConfigurable).asDisabledProxyAuthPromptsManager(): DisabledProxyAuthPromptsManager = HttpConfigurableToDisabledPromptsManager(this)

private class HttpConfigurableToCredentialStoreAdapter(private val getHttpConfigurable: () -> HttpConfigurable) : ProxyCredentialStore {
  private val httpConfigurable: HttpConfigurable get() = getHttpConfigurable()
  private var buffer: ByteBuffer? = null

  // host is not checked in com.intellij.util.net.HttpConfigurable.getPromptedAuthentication, but here we check it
  // theoretically might change the behavior, but shouldn't be critical

  @Synchronized
  override fun getCredentials(host: String, port: Int): Credentials? = when {
    httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port -> {
      val credentials = httpConfigurable.readCredentials()
      if (credentials == null || httpConfigurable.KEEP_PROXY_PASSWORD) credentials else {
        val password = buffer?.let {
          val bytes = ByteArray(it.limit())
          it.get(0, bytes)
          bytes
        }
        Credentials(credentials.userName, password)
      }
    }
    else -> httpConfigurable.getGenericPassword(host, port)?.toCredentials()
  }

  @Synchronized
  override fun setCredentials(host: String, port: Int, credentials: Credentials?, remember: Boolean) {
    if (httpConfigurable.PROXY_HOST == host && httpConfigurable.PROXY_PORT == port) {
      buffer = null
      if (credentials == null || remember) {
        httpConfigurable.writeCredentials(credentials)
      }
      else {
        httpConfigurable.writeCredentials(Credentials(credentials.userName, password = null as String?))
        buffer = credentials.password?.let {
          val buffer = ByteBuffer.allocateDirect(it.length)
          buffer.put(0, it.toByteArray())
          buffer
        }
      }
      httpConfigurable.KEEP_PROXY_PASSWORD = credentials != null && remember
    }
    else if (credentials?.password != null) {
      httpConfigurable.putGenericPassword(host, port, PasswordAuthentication(credentials.userName, credentials.password!!.toCharArray()), remember)
    }
    else {
      httpConfigurable.removeGeneric(CommonProxy.HostInfo(null, host, port))
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
    buffer = null
    httpConfigurable.writeCredentials(null)
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
