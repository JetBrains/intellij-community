// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SystemProperties
import com.intellij.util.net.NetUtils.isLocalhost
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.*
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

class IdeProxySelector(
  private val configurationProvider: ProxyConfigurationProvider,
) : ProxySelector() {
  private val autoProxyResult = AtomicReference<AutoProxyHolder?>()
  private val exceptionsMatcher = AtomicReference<ExceptionsMatcherHolder?>()

  override fun select(uri: URI?): List<Proxy> {
    logger.debug { "$uri: select" }
    if (uri == null) {
      logger.debug { "$uri: no proxy, uri is null" }
      return NO_PROXY_LIST
    }

    if (!("http" == uri.scheme || "https" == uri.scheme)) {
      logger.debug { "$uri: no proxy, not http/https scheme: ${uri.scheme}" }
      return NO_PROXY_LIST
    }

    if (isLocalhost(uri.host ?: "")) {
      logger.debug { "$uri: no proxy, localhost" }
      return NO_PROXY_LIST
    }

    val conf = try {
      configurationProvider.getProxyConfiguration()
    }
    catch (_: CancellationException) {
      logger.debug { "$uri: no proxy, cancelled" }
      return NO_PROXY_LIST
    }
    catch (e: Exception) {
      logger.error("$uri: no proxy, failed to get proxy configuration", e)
      return NO_PROXY_LIST
    }

    when (conf) {
      is ProxyConfiguration.DirectProxy -> {
        logger.debug { "$uri: no proxy, DIRECT configuration" }
        return NO_PROXY_LIST
      }
      is ProxyConfiguration.AutoDetectProxy, is ProxyConfiguration.ProxyAutoConfiguration -> {
        return selectUsingPac((conf as? ProxyConfiguration.ProxyAutoConfiguration)?.pacUrl, uri)
      }
      is ProxyConfiguration.StaticProxyConfiguration -> {
        if (getExceptionsMatcher(conf.exceptions).test(uri.host ?: "")) {
          logger.debug { "$uri: no proxy, uri is in exception list" }
          return NO_PROXY_LIST
        }
        val proxy = conf.asJavaProxy()
        logger.debug { "$uri: proxy $proxy" }
        return Collections.singletonList(proxy)
      }
      else -> {
        logger.warn("$uri: no proxy, unknown proxy configuration: $conf")
        return NO_PROXY_LIST
      }
    }
  }

  private fun getExceptionsMatcher(exceptions: String): Predicate<String> {
    val cached = exceptionsMatcher.get()
    if (cached?.exceptions == exceptions) {
      return cached.matcher
    }
    val matcher = ProxyConfiguration.buildProxyExceptionsMatcher(exceptions)
    exceptionsMatcher.set(ExceptionsMatcherHolder(exceptions, matcher))
    return matcher
  }

  private fun selectUsingPac(pacUrl: URL?, uri: URI): List<Proxy> {
    // https://youtrack.jetbrains.com/issue/IDEA-262173
    val oldDocumentBuilderFactory = System.setProperty(DOCUMENT_BUILDER_FACTORY_KEY, "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl")
    try {
      val selector = getAutoProxySelector(pacUrl)
      try {
        val result = selector.select(uri)
        logger.debug { "$uri: pac/autodetect proxy select result: $result" }
        return result
      } catch (_: StackOverflowError) {
        logger.warn("$uri: no proxy, too large PAC script (JRE-247)")
        return NO_PROXY_LIST
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      logger.error("$uri: no proxy, failed to select using PAC/autodetect", e)
      return NO_PROXY_LIST
    }
    finally {
      SystemProperties.setProperty(DOCUMENT_BUILDER_FACTORY_KEY, oldDocumentBuilderFactory)
    }
  }

  private fun getAutoProxySelector(pacUrl: URL?): ProxySelector {
    val autoProxy = autoProxyResult.get()
    if (autoProxy != null && autoProxy.pacUrl?.toString() == pacUrl?.toString()) return autoProxy.selector
    synchronized(this) {
      val autoProxy = autoProxyResult.get()
      if (autoProxy != null && autoProxy.pacUrl?.toString() == pacUrl?.toString()) return autoProxy.selector

      val searchStartMs = System.currentTimeMillis()
      val detectedSelector = try {
        NetUtils.getProxySelector(pacUrl?.toString())
      }
      catch (e: Exception) {
        logger.warn("proxy auto-configuration has failed ${pacUrl?.let { "(url=$it)" }}", e)
        null
      }
      val resultSelector = detectedSelector ?: DirectSelector.also {
        if (pacUrl != null) {
          logger.warn("failed to configure proxy by pacUrl=$pacUrl, using NO_PROXY")
        }
        else {
          logger.info("unable to autodetect proxy settings, using NO_PROXY")
        }
      }
      if (pacUrl == null) {
        proxyAutodetectDurationMs = System.currentTimeMillis() - searchStartMs
      }
      autoProxyResult.set(AutoProxyHolder(pacUrl, resultSelector))
      return resultSelector
    }
  }

  override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
    // MAYBE adjust the selection result; in previous implementation (IdeaWideProxySelector) it was effectively no-op
  }

  companion object {
    private val logger = logger<IdeProxySelector>()

    private const val DOCUMENT_BUILDER_FACTORY_KEY = "javax.xml.parsers.DocumentBuilderFactory"

    // holds either autodetected proxy or a pac proxy, pac url is null if autodetect is used
    private data class AutoProxyHolder(val pacUrl: URL?, val selector: ProxySelector)

    private data class ExceptionsMatcherHolder(val exceptions: String, val matcher: Predicate<String>)

    @Volatile
    private var proxyAutodetectDurationMs: Long = -1L

    /**
     * @return duration that proxy auto-detection took (ms), or -1 in case automatic proxy detection wasn't triggered
     */
    @ApiStatus.Internal
    fun getProxyAutoDetectDurationMs(): Long = proxyAutodetectDurationMs

    private object DirectSelector : ProxySelector() {
      override fun select(uri: URI?): List<Proxy?>? = NO_PROXY_LIST
      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }
  }
}