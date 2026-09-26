// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProxyUtils")
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.isFulfilled
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
import com.intellij.util.proxy.JavaProxyProperty
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import javax.swing.JComponent

fun Proxy.isRealProxy(): Boolean = Proxy.NO_PROXY != this && Proxy.Type.DIRECT != this.type()

/**
 * **NB:** does not honor [exceptions][com.intellij.util.net.ProxyConfiguration.StaticProxyConfiguration.exceptions].
 */
fun ProxyConfiguration.StaticProxyConfiguration.asJavaProxy(): Proxy = Proxy(
  when (protocol) {
    ProxyProtocol.HTTP -> Proxy.Type.HTTP
    ProxyProtocol.SOCKS -> Proxy.Type.SOCKS
  },
  InetSocketAddress.createUnresolved(host, port)
)

/**
 * **NB:** consider the security implications of using credentials as properties, given that they are stored in plain text.
 */
fun getCurrentSettingsAsJvmProperties(): Map<String, String> {
  val configuration = ProxySettings.getInstance().getProxyConfiguration()
  val credentials = ProxyCredentialStore.getInstance().getCredentials(configuration)
  return getJvmProperties(configuration, credentials)
}

@VisibleForTesting
@ApiStatus.Internal
fun getJvmProperties(configuration: ProxyConfiguration, credentials: Credentials?): Map<String, String> {
  if (configuration is ProxyConfiguration.StaticProxyConfiguration) {
    val result = mutableMapOf<String, String>()
    when (configuration.protocol) {
      ProxyProtocol.HTTP -> {
        result[JavaProxyProperty.HTTP_HOST] = configuration.host
        result[JavaProxyProperty.HTTP_PORT] = configuration.port.toString()
        result[JavaProxyProperty.HTTPS_HOST] = configuration.host
        result[JavaProxyProperty.HTTPS_PORT] = configuration.port.toString()
        if (credentials != null && credentials.isFulfilled()) {
          val userName = credentials.userName!!
          val password = credentials.getPasswordAsString()!!
          result[JavaProxyProperty.HTTP_PROXY_USER] = userName
          result[JavaProxyProperty.HTTP_PROXY_PASSWORD] = password
          result[JavaProxyProperty.HTTPS_PROXY_USER] = userName
          result[JavaProxyProperty.HTTPS_PROXY_PASSWORD] = password
        }
      }
      ProxyProtocol.SOCKS -> {
        result[JavaProxyProperty.SOCKS_HOST] = configuration.host
        result[JavaProxyProperty.SOCKS_PORT] = configuration.port.toString()
        if (credentials != null && credentials.isFulfilled()) {
          result[JavaProxyProperty.SOCKS_USERNAME] = credentials.userName!!
          result[JavaProxyProperty.SOCKS_PASSWORD] = credentials.getPasswordAsString()!!
        }
      }
    }
    if (configuration.exceptions.isNotBlank()) {
      result[JavaProxyProperty.HTTP_NON_PROXY_HOSTS] = configuration.exceptions.split(',').joinToString("|", transform = String::trim)
    }
    return result.toMap()
  }
  else {
    return emptyMap()
  }
}

/**
 * **NB:** consider the security implications of using credentials as properties, given that they are stored in plain text.
 */
fun getDetectedSettingsAsJvmProperties(uri: URI): List<Map<String, String>> = getDetectedSettingsAsJvmProperties(
  uri, JdkProxyProvider.getInstance().proxySelector, ProxyCredentialStore.getInstance()::getCredentials
)

@VisibleForTesting
@ApiStatus.Internal
fun getDetectedSettingsAsJvmProperties(
  uri: URI,
  proxySelector: ProxySelector,
  credentialProvider: (String, Int) -> Credentials?
): List<Map<String, String>> {
  return proxySelector.select(uri).mapNotNull { proxy ->
    val address = proxy.address()
    if (proxy.isRealProxy() && address is InetSocketAddress) {
      val proto = if (proxy.type() == Proxy.Type.SOCKS) ProxyProtocol.SOCKS else ProxyProtocol.HTTP
      val configuration = ProxyConfiguration.proxy(proto, address.hostString, address.port)
      val credentials = credentialProvider(address.hostString, address.port)
      getJvmProperties(configuration, credentials)
    }
    else null
  }
}

@Deprecated("Leads to overcomplicated code; use `ProxyCredentialStore.getCredentials(ProxyConfiguration)` instead", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
fun ProxyConfiguration.StaticProxyConfiguration.asJvmProperties(credentialProvider: ProxyCredentialProvider?): Map<String, String> {
  return getJvmProperties(this, credentialProvider?.getCredentials(host, port))
}

@Deprecated("Use HttpProxyConfigurable.editConfigurable", replaceWith = ReplaceWith("HttpProxyConfigurable.editConfigurable(parent)"), level = DeprecationLevel.ERROR)
@Suppress("UnusedReceiverParameter")
fun ProxySettings.editConfigurable(parent: JComponent?): Boolean = HttpProxyConfigurable.editConfigurable(parent)

@Deprecated("Leads to overcomplicated code; use `ProxyCredentialStore.getCredentials(ProxyConfiguration)` instead", level = DeprecationLevel.ERROR)
@Suppress("DEPRECATION")
fun ProxySettings.getStaticProxyCredentials(credentialStore: ProxyCredentialProvider): Credentials? {
  val conf = getProxyConfiguration()
  if (conf !is ProxyConfiguration.StaticProxyConfiguration) return null
  return credentialStore.getCredentials(conf.host, conf.port)
}

fun getHostNameReliably(requestingHost: String?, requestingSite: InetAddress?, requestingUrl: URL?): String? {
  /** from [com.intellij.util.proxy.CommonProxy.getHostNameReliably] */
  return requestingHost
         ?: requestingSite?.hostName
         ?: requestingUrl?.host
}

@JvmField
val NO_PROXY_LIST: List<Proxy> = listOf(Proxy.NO_PROXY)
