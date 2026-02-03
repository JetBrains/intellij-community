// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProxyUtils")
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.isFulfilled
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
import com.intellij.util.proxy.JavaProxyProperty
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL
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
  return when (type()) {
    Proxy.Type.SOCKS -> mapOf(
      JavaProxyProperty.SOCKS_HOST to address.hostString,
      JavaProxyProperty.SOCKS_PORT to address.port.toString(),
    )
    Proxy.Type.HTTP -> mapOf(
      JavaProxyProperty.HTTP_HOST to address.hostString,
      JavaProxyProperty.HTTP_PORT to address.port.toString(),
      JavaProxyProperty.HTTPS_HOST to address.hostString,
      JavaProxyProperty.HTTPS_PORT to address.port.toString(),
    )
    else -> emptyMap()
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

/**
 * @param credentialProvider known credentials will be added as properties to the resulting map.
 *                           Please, consider the security implications of using credentials as properties, given that they are in plain text form.
 */
fun ProxyConfiguration.StaticProxyConfiguration.asJvmProperties(credentialProvider: ProxyCredentialProvider?): Map<String, String> {
  val javaProxy = asJavaProxy()
  val jvmPropertiesWithCredentials = javaProxy.asJvmPropertiesWithCredentials(credentialProvider)
  return if (jvmPropertiesWithCredentials.isEmpty() || exceptions.isEmpty()) {
    jvmPropertiesWithCredentials
  }
  else {
    jvmPropertiesWithCredentials + (JavaProxyProperty.HTTP_NON_PROXY_HOSTS to exceptions.split(',').joinToString("|", transform = String::trim))
  }
}

@Deprecated("Use HttpProxyConfigurable.editConfigurable", replaceWith = ReplaceWith("HttpProxyConfigurable.editConfigurable(parent)"), level = DeprecationLevel.ERROR)
@Suppress("UnusedReceiverParameter")
fun ProxySettings.editConfigurable(parent: JComponent?): Boolean = HttpProxyConfigurable.editConfigurable(parent)

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
  val props = asJvmProperties()
  val address = address()
  if (props.isEmpty() || address !is InetSocketAddress || credentialProvider == null) {
    return props
  }
  return props + credentialProvider.getCredentialsAsJvmProperties(address.hostString, address.port, type())
}

private fun ProxyCredentialProvider.getCredentialsAsJvmProperties(host: String, port: Int, proxyType: Proxy.Type): Map<String, String> {
  val credentials = getCredentials(host, port)
  if (credentials == null || !credentials.isFulfilled()) {
    return emptyMap()
  }
  val username = credentials.userName!!
  val password = credentials.password!!.toString()
  return when (proxyType) {
    Proxy.Type.SOCKS -> mapOf(
      JavaProxyProperty.SOCKS_USERNAME to username,
      JavaProxyProperty.SOCKS_PASSWORD to password,
    )
    Proxy.Type.HTTP -> mapOf(
      JavaProxyProperty.HTTP_PROXY_USER to username,
      JavaProxyProperty.HTTP_PROXY_PASSWORD to password,
      JavaProxyProperty.HTTPS_PROXY_USER to username,
      JavaProxyProperty.HTTPS_PROXY_PASSWORD to password,
    )
    else -> emptyMap()
  }
}
