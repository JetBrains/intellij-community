// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:Suppress("removal", "DEPRECATION")

package com.intellij.util.net.internal

import com.intellij.credentialStore.Credentials
import com.intellij.util.net.DisabledProxyAuthPromptsManager
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
import com.intellij.util.net.ProxyConfigurationProvider
import com.intellij.util.net.ProxyCredentialProvider
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import com.intellij.util.proxy.CommonProxy
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.net.MalformedURLException
import java.net.PasswordAuthentication
import java.net.URL

fun HttpConfigurable.getProxyConfiguration(): ProxyConfiguration {
  try {
    return when {
      USE_PROXY_PAC -> {
        val pacUrl = PAC_URL
        if (USE_PAC_URL && !pacUrl.isNullOrEmpty()) {
          try {
            ProxyConfiguration.proxyAutoConfiguration(URL(pacUrl))
          } catch (_: MalformedURLException) {
            ProxyConfiguration.autodetect
          }
        }
        else {
          ProxyConfiguration.autodetect
        }
      }
      USE_HTTP_PROXY -> {
        ProxyConfiguration.proxy(
          if (PROXY_TYPE_IS_SOCKS) ProxyProtocol.SOCKS else ProxyProtocol.HTTP,
          PROXY_HOST,
          PROXY_PORT,
          PROXY_EXCEPTIONS ?: ""
        )
      }
      else -> { // USE_NO_PROXY
        ProxyConfiguration.direct
      }
    }
  }
  catch (_: IllegalArgumentException) { // just in case
    return ProxySettings.defaultProxyConfiguration
  }
}

fun HttpConfigurable.setFromProxyConfiguration(proxyConf: ProxyConfiguration) {
  when (proxyConf) {
    is ProxyConfiguration.DirectProxy -> {
      USE_HTTP_PROXY = false
      USE_PROXY_PAC = false
    }
    is ProxyConfiguration.AutoDetectProxy -> {
      USE_HTTP_PROXY = false
      USE_PROXY_PAC = true
      USE_PAC_URL = false
      PAC_URL = null
    }
    is ProxyConfiguration.ProxyAutoConfiguration -> {
      USE_HTTP_PROXY = false
      USE_PROXY_PAC = true
      USE_PAC_URL = true
      PAC_URL = proxyConf.pacUrl.toString()
    }
    is ProxyConfiguration.StaticProxyConfiguration -> {
      USE_PROXY_PAC = false
      USE_HTTP_PROXY = true
      PROXY_TYPE_IS_SOCKS = proxyConf.protocol == ProxyProtocol.SOCKS
      PROXY_HOST = proxyConf.host
      PROXY_PORT = proxyConf.port
      PROXY_EXCEPTIONS = proxyConf.exceptions
    }
  }
}

fun HttpConfigurable.getCredentials(): Credentials? {
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

fun HttpConfigurable.setCredentials(credentials: Credentials?) {
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

fun PasswordAuthentication.toCredentials(): Credentials = Credentials(userName, password)

fun (() -> HttpConfigurable).asProxySettings(): ProxySettings = HttpConfigurableToProxySettingsAdapter(this)
fun (() -> HttpConfigurable).asProxyCredentialStore(): ProxyCredentialStore = HttpConfigurableToCredentialStoreAdapter(this)
fun (() -> HttpConfigurable).asDisabledProxyAuthPromptsManager(): DisabledProxyAuthPromptsManager = HttpConfigurableToDisabledPromptsManager(this)

private class HttpConfigurableToProxySettingsAdapter(private val getHttpConfigurable: () -> HttpConfigurable) : ProxySettings, ProxyConfigurationProvider {
  override fun getProxyConfiguration(): ProxyConfiguration = getHttpConfigurable().getProxyConfiguration()
  override fun setProxyConfiguration(proxyConfiguration: ProxyConfiguration) = getHttpConfigurable().setFromProxyConfiguration(proxyConfiguration)
}

private class HttpConfigurableToCredentialStoreAdapter(private val getHttpConfigurable: () -> HttpConfigurable) : ProxyCredentialStore, ProxyCredentialProvider {
  private val httpConfigurable: HttpConfigurable get() = getHttpConfigurable()

  // host is not checked in com.intellij.util.net.HttpConfigurable.getPromptedAuthentication, but here we check it
  // theoretically might change the behavior, but shouldn't be critical

  @Synchronized
  override fun getCredentials(host: String, port: Int): Credentials? {
    val conf = httpConfigurable.getProxyConfiguration()
    if (conf is ProxyConfiguration.StaticProxyConfiguration && conf.host == host && conf.port == port) {
      return httpConfigurable.getCredentials()
    }
    else {
      return httpConfigurable.getGenericPassword(host, port)?.toCredentials()
    }
  }

  @Synchronized
  override fun setCredentials(host: String, port: Int, credentials: Credentials?, remember: Boolean) {
    val conf = httpConfigurable.getProxyConfiguration()
    if (conf is ProxyConfiguration.StaticProxyConfiguration && conf.host == host && conf.port == port) {
      httpConfigurable.setCredentials(credentials)
      httpConfigurable.KEEP_PROXY_PASSWORD = credentials != null && remember
    }
    else {
      if (credentials == null || credentials.password == null) {
        httpConfigurable.removeGeneric(CommonProxy.HostInfo(null, host, port))
      }
      else {
        httpConfigurable.putGenericPassword(host, port, PasswordAuthentication(credentials.userName, credentials.password!!.toCharArray()), remember)
      }
    }
  }

  @Synchronized
  override fun areCredentialsRemembered(host: String, port: Int): Boolean {
    val conf = httpConfigurable.getProxyConfiguration()
    return if (conf is ProxyConfiguration.StaticProxyConfiguration && conf.host == host && conf.port == port) {
      httpConfigurable.KEEP_PROXY_PASSWORD
    }
    else {
      httpConfigurable.isGenericPasswordRemembered(host, port)
    }
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

private class HttpConfigurableToDisabledPromptsManager(val getHttpConfigurable: () -> HttpConfigurable) : DisabledProxyAuthPromptsManager {
  private val httpConfigurable get() = getHttpConfigurable()

  @Synchronized
  override fun disablePromptedAuthentication(host: String, port: Int) {
    val conf = httpConfigurable.getProxyConfiguration()
    if (conf is ProxyConfiguration.StaticProxyConfiguration && conf.host == host && conf.port == port) {
      httpConfigurable.AUTHENTICATION_CANCELLED = true
    }
    else {
      httpConfigurable.setGenericPasswordCanceled(host, port)
    }
  }

  @Synchronized
  override fun isPromptedAuthenticationDisabled(host: String, port: Int): Boolean {
    val conf = httpConfigurable.getProxyConfiguration()
    if (conf is ProxyConfiguration.StaticProxyConfiguration && conf.host == host && conf.port == port) {
      return httpConfigurable.AUTHENTICATION_CANCELLED
    }
    else {
      return httpConfigurable.isGenericPasswordCanceled(host, port)
    }
  }

  @Synchronized
  override fun enablePromptedAuthentication(host: String, port: Int) {
    val conf = httpConfigurable.getProxyConfiguration()
    if (conf is ProxyConfiguration.StaticProxyConfiguration && conf.host == host && conf.port == port) {
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