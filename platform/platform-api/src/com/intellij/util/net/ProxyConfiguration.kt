// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.util.text.StringUtil
import java.net.URL
import java.util.function.Predicate
import java.util.regex.Pattern

/**
 * Represents types of proxies that can be configured by the user and which are supported by the IDE.
 *
 * If you need to know the exact type of [ProxyConfiguration], check the instance against [StaticProxyConfiguration], [ProxyAutoConfiguration], etc.
 *
 * To instantiate a [ProxyConfiguration], use one of [direct], [autodetect], [proxy], or [proxyAutoConfiguration].
 *
 * This class does not handle authentication or credentials.
 *
 * @see ProxyAuthentication
 * @see ProxySettings
 */
sealed interface ProxyConfiguration {
  companion object {
    /**
     * Use no proxy.
     */
    @JvmStatic
    val direct: DirectProxy get() = DirectProxyData

    /**
     * Automatically determine proxy settings using java system properties, OS settings or environment variables.
     */
    @JvmStatic
    val autodetect: AutoDetectProxy get() = AutoDetectProxyData

    /**
     * @param exceptions comma-delimited list of host globs which must not be proxied, e.g. `*.domain.com,192.168.*`
     */
    @JvmStatic
    fun proxy(protocol: ProxyProtocol, host: String, port: Int = protocol.defaultPort, exceptions: String = ""): StaticProxyConfiguration {
      require(port in 0..65535) {
        "port is invalid: $port"
      }
      require(NetUtils.isValidHost(host) != NetUtils.ValidHostInfo.INVALID) {
        "host is invalid: $host"
      }
      return StaticProxyConfigurationData(protocol, host, port, exceptions)
    }

    /**
     * also known as PAC
     */
    @JvmStatic
    fun proxyAutoConfiguration(pacUrl: URL): ProxyAutoConfiguration = ProxyAutoConfigurationData(pacUrl)

    /**
     * @param exceptions as in [com.intellij.util.net.ProxyConfiguration.proxy]
     * @return a predicate that tests if the provided URI host is an exception for proxying
     */
    @JvmStatic
    fun buildProxyExceptionsMatcher(exceptions: String): Predicate<String> {
      if (exceptions.isBlank()) {
        return Predicate { false }
      }
      val regexp = exceptions
        .split(",")
        .map { StringUtil.escapeToRegexp(it.trim()).replace("\\*", ".*") }
        .joinToString("|")
      return Pattern.compile(regexp).asMatchPredicate()
    }
  }

  enum class ProxyProtocol(val defaultPort: Int) {
    HTTP(80),
    SOCKS(1080)
  }

  interface StaticProxyConfiguration : ProxyConfiguration {
    val protocol: ProxyProtocol
    val host: String
    val port: Int

    /**
     * comma-delimited list of host globs which must not be proxied
     */
    val exceptions: String
  }

  interface ProxyAutoConfiguration : ProxyConfiguration {
    val pacUrl: URL
  }

  interface AutoDetectProxy : ProxyConfiguration

  interface DirectProxy : ProxyConfiguration

  private data class StaticProxyConfigurationData(
    override val protocol: ProxyProtocol,
    override val host: String,
    override val port: Int = protocol.defaultPort,
    override val exceptions: String = ""
  ) : StaticProxyConfiguration

  private data class ProxyAutoConfigurationData(override val pacUrl: URL) : ProxyAutoConfiguration

  private data object AutoDetectProxyData : AutoDetectProxy

  private data object DirectProxyData : DirectProxy
}