// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProxyUtils")
@file:Suppress("removal", "DEPRECATION")

package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.isFulfilled
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
import com.intellij.util.proxy.JavaProxyProperty
import java.net.*
import javax.swing.JComponent

fun Proxy.isRealProxy(): Boolean {
  return Proxy.NO_PROXY != this && Proxy.Type.DIRECT != this.type()
}

fun Proxy.asJvmProperties(): Map<String, String> {
  if (!isRealProxy()) {
    return emptyMap()
  }
  val address = address()
  if (address !is InetSocketAddress) {
    return emptyMap()
  }
  return buildMap {
    when (type()) {
      Proxy.Type.SOCKS -> {
        put(JavaProxyProperty.SOCKS_HOST, address.hostString)
        put(JavaProxyProperty.SOCKS_PORT, address.port.toString())
      }
      Proxy.Type.HTTP -> {
        put(JavaProxyProperty.HTTP_HOST, address.hostString)
        put(JavaProxyProperty.HTTP_PORT, address.port.toString())
        put(JavaProxyProperty.HTTPS_HOST, address.hostString)
        put(JavaProxyProperty.HTTPS_PORT, address.port.toString())
      }
      else -> {}
    }
  }
}

/**
 * N.B.: does not honor [exceptions][com.intellij.util.net.ProxyConfiguration.StaticProxyConfiguration.exceptions].
 */
fun ProxyConfiguration.StaticProxyConfiguration.asJavaProxy(): Proxy = Proxy(
  when (protocol) {
    ProxyProtocol.HTTP -> Proxy.Type.HTTP
    ProxyProtocol.SOCKS -> Proxy.Type.SOCKS
  },
  InetSocketAddress.createUnresolved(host, port)
)

fun ProxyConfiguration.StaticProxyConfiguration.asJvmProperties(credentialProvider: ProxyCredentialProvider?): Map<String, String> {
  val javaProxy = asJavaProxy()
  val jvmPropertiesWithCredentials = javaProxy.asJvmPropertiesWithCredentials(credentialProvider)
  return if (jvmPropertiesWithCredentials.isEmpty() || exceptions.isEmpty()) {
    jvmPropertiesWithCredentials
  }
  else {
    jvmPropertiesWithCredentials + (JavaProxyProperty.HTTP_NON_PROXY_HOSTS to exceptions.replace(",", "|"))
  }
}

fun ProxySettings.editConfigurable(parent: JComponent?): Boolean {
  return ShowSettingsUtil.getInstance().editConfigurable(parent, HttpProxyConfigurable(this))
}

fun ProxySettings.getStaticProxyCredentials(credentialStore: ProxyCredentialProvider): Credentials? {
  val conf = getProxyConfiguration()
  if (conf !is ProxyConfiguration.StaticProxyConfiguration) return null
  return credentialStore.getCredentials(conf.host, conf.port)
}

fun ProxySettings.setStaticProxyCredentials(credentialStore: ProxyCredentialStore, value: Credentials?, remember: Boolean) {
  val conf = getProxyConfiguration()
  if (conf !is ProxyConfiguration.StaticProxyConfiguration) return
  credentialStore.setCredentials(conf.host, conf.port, value, remember)
}

fun getHostNameReliably(requestingHost: String?, requestingSite: InetAddress?, requestingUrl: URL?): String? {
  /** from [com.intellij.util.proxy.CommonProxy.getHostNameReliably] */
  return requestingHost
         ?: requestingSite?.hostName
         ?: requestingUrl?.host
}

@JvmField
val NO_PROXY_LIST: List<Proxy> = listOf(Proxy.NO_PROXY)

/**
 * @param credentialProvider specify a non-null value in case credentials should be included as properties (if they are known).
 * Use [ProxyAuthentication.getInstance] for the default proxy credential provider.
 * @return a list of non-direct proxy configurations for the specified [URI]. Each element is a map consisting of corresponding jvm properties.
 */
fun URI.getApplicableProxiesAsJvmProperties(
  credentialProvider: ProxyCredentialProvider?,
  proxySelector: ProxySelector = JdkProxyProvider.getInstance().proxySelector,
): List<Map<String, String>> {
  return proxySelector.select(this)
    .map { proxy ->
      proxy.asJvmPropertiesWithCredentials(credentialProvider)
    }
    .filter {
      it.isNotEmpty()
    }
}

private fun Proxy.asJvmPropertiesWithCredentials(credentialProvider: ProxyCredentialProvider?): Map<String, String> {
  val props = asJvmProperties().toMutableMap()
  if (credentialProvider == null || props.isEmpty()) {
    return props
  }
  val address = address()
  if (address !is InetSocketAddress) {
    return emptyMap()
  }
  val credentials = credentialProvider.getCredentials(address.hostString, address.port)
  if (credentials == null || !credentials.isFulfilled()) {
    return emptyMap()
  }
  val proxyType = type()
  val usernameProp = if (proxyType == Proxy.Type.SOCKS) JavaProxyProperty.SOCKS_USERNAME else JavaProxyProperty.HTTP_USERNAME
  val passwordProp = if (proxyType == Proxy.Type.SOCKS) JavaProxyProperty.SOCKS_PASSWORD else JavaProxyProperty.HTTP_PASSWORD
  props[usernameProp] = credentials.userName!!
  props[passwordProp] = credentials.password!!.toString()
  return props
}