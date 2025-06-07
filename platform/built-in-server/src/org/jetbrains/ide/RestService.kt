// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.stream.MalformedJsonException
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.showYesNoDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AppIcon
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.getHostName
import com.intellij.util.io.origin
import com.intellij.util.io.referrer
import com.intellij.xml.util.XmlStringUtil
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.builtInWebServer.BuiltInWebServerAuth
import org.jetbrains.io.addNoCache
import org.jetbrains.io.response
import org.jetbrains.io.responseStatus
import org.jetbrains.io.send
import org.jetbrains.io.sendPlainText
import java.awt.Window
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Document your service using [apiDoc](http://apidocjs.com).
 * To extract a big example from source code, consider adding a `*.coffee` file near the sources
 * (or Python/Ruby, but CoffeeScript is recommended because its plugin is lightweight).
 * See [AboutHttpService] for example.
 *
 * Don't create [JsonReader]/[JsonWriter] directly, use only provided [RestService.createJsonReader] and [RestService.createJsonWriter] methods
 * (to ensure that you handle in/out according to REST API guidelines).
 *
 * @see <a href="http://www.vinaysahni.com/best-practices-for-a-pragmatic-restful-api">Best Practices for Designing a Pragmatic REST API</a>.
 */
abstract class RestService : HttpRequestHandler() {
  companion object {
    @JvmField
    val LOG: Logger = logger<RestService>()

    const val PREFIX: String = "api"

    @JvmStatic
    fun activateLastFocusedFrame() {
      (IdeFocusManager.getGlobalInstance().lastFocusedFrame as? Window)?.toFront()
    }

    @JvmStatic
    fun createJsonReader(request: FullHttpRequest): JsonReader =
      JsonReader(ByteBufInputStream(request.content()).reader())
        .apply { strictness = Strictness.LENIENT }

    @JvmStatic
    fun createJsonWriter(out: OutputStream): JsonWriter =
      JsonWriter(out.writer())
        .apply { setIndent("  ") }

    @JvmStatic
    fun getLastFocusedOrOpenedProject(): Project? =
      IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: ProjectManager.getInstance().openProjects.firstOrNull()

    @JvmStatic
    fun sendOk(request: FullHttpRequest, context: ChannelHandlerContext) {
      sendStatus(HttpResponseStatus.OK, HttpUtil.isKeepAlive(request), context.channel())
    }

    @JvmStatic
    fun sendStatus(status: HttpResponseStatus, keepAlive: Boolean, channel: Channel) {
      responseStatus(status, keepAlive, channel)
    }

    @JvmStatic
    fun send(byteOut: BufferExposingByteArrayOutputStream, request: HttpRequest, context: ChannelHandlerContext) {
      val response = response("application/json", Unpooled.wrappedBuffer(byteOut.internalBuffer, 0, byteOut.size()))
      sendResponse(request, context, response)
    }

    @JvmStatic
    fun sendResponse(request: HttpRequest, context: ChannelHandlerContext, response: HttpResponse) {
      response.addNoCache()
      response.headers().set("X-Frame-Options", "Deny")
      response.send(context.channel(), request)
    }

    @Suppress("SameParameterValue")
    @JvmStatic
    fun getStringParameter(name: String, urlDecoder: QueryStringDecoder): String? = urlDecoder.parameters()[name]?.lastOrNull()

    @JvmStatic
    fun getIntParameter(name: String, urlDecoder: QueryStringDecoder): Int {
      return getStringParameter(name, urlDecoder)?.ifBlank { null }?.toIntOrNull() ?: -1
    }

    @JvmOverloads
    @JvmStatic
    fun getBooleanParameter(name: String, urlDecoder: QueryStringDecoder, defaultValue: Boolean = false): Boolean {
      val values = urlDecoder.parameters()[name] ?: return defaultValue
      // if just name specified, so, true
      val value = values.lastOrNull() ?: return true
      return value.toBoolean()
    }

    fun parameterMissedErrorMessage(name: String): String = "Parameter \"$name\" is not specified"
  }

  protected val gson: Gson by lazy {
    GsonBuilder()
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .create()
  }

  private val abuseCounter = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build<Any, AtomicInteger>(CacheLoader { AtomicInteger() })

  private val trustedOrigins = Caffeine.newBuilder()
    .maximumSize(1024)
    .expireAfterWrite(1, TimeUnit.DAYS)
    .build<Pair<String, String>, Boolean>()

  private val hostLocks = CollectionFactory.createConcurrentWeakKeyWeakValueMap<String, Any>()

  private var isBlockUnknownHosts = false

  /**
   * Service url must be "/api/$serviceName", but to preserve backward compatibility, prefixless path could be also supported
   */
  protected open val isPrefixlessAllowed: Boolean
    get() = false

  /**
   * Whether service failures should be returned as HTML or PlainText.
   */
  protected open val reportErrorsAsPlainText: Boolean
    get() = false

  /**
   * Use human-readable name or UUID if it is an internal service.
   */
  @NlsSafe
  protected abstract fun getServiceName(): String

  override fun isSupported(request: FullHttpRequest): Boolean {
    if (!isMethodSupported(request.method())) {
      return false
    }

    val uri = request.uri()

    if (isPrefixlessAllowed && checkPrefix(uri, getServiceName())) {
      return true
    }

    val serviceName = getServiceName()
    val minLength = 1 + PREFIX.length + 1 + serviceName.length
    if (uri.length >= minLength &&
        uri[0] == '/' &&
        uri.regionMatches(1, PREFIX, 0, PREFIX.length, ignoreCase = true) &&
        uri.regionMatches(2 + PREFIX.length, serviceName, 0, serviceName.length, ignoreCase = true)) {
      if (uri.length == minLength) {
        return true
      }
      else {
        val c = uri[minLength]
        return c == '/' || c == '?'
      }
    }
    return false
  }

  protected open fun isMethodSupported(method: HttpMethod): Boolean = method === HttpMethod.GET

  /**
   * If the requests per minute counter exceeds this value, the exception [HttpResponseStatus.TOO_MANY_REQUESTS] will be sent.
   * @return The value of "ide.rest.api.requests.per.minute" Registry key or '30', if the key does not exist.
   */
  protected open fun getMaxRequestsPerMinute(): Int = Registry.intValue("ide.rest.api.requests.per.minute", 30)

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    try {
      if (!isHostTrusted(request, urlDecoder)) {
        HttpResponseStatus.FORBIDDEN.sendError(context.channel(), request)
        return true
      }

      val counter = abuseCounter.get(getRequesterId(urlDecoder, request, context))!!
      if (counter.incrementAndGet() > getMaxRequestsPerMinute()) {
        HttpResponseStatus.TOO_MANY_REQUESTS.sendError(context.channel(), request)
        return true
      }

      val error = execute(urlDecoder, request, context)
      if (error != null) {
        HttpResponseStatus.BAD_REQUEST.sendError(context.channel(), request, error)
      }
    }
    catch (e: Throwable) {
      val status: HttpResponseStatus?
      // JsonReader exception
      if (e is MalformedJsonException || e is IllegalStateException && e.message!!.startsWith("Expected a ")) {
        LOG.warn(e)
        status = HttpResponseStatus.BAD_REQUEST
      }
      else {
        LOG.error(e)
        status = HttpResponseStatus.INTERNAL_SERVER_ERROR
      }

      status.sendError(context.channel(), request, XmlStringUtil.escapeString(ExceptionUtil.getThrowableText(e)))
    }

    return true
  }

  private fun HttpResponseStatus.sendError(
    channel: Channel,
    request: HttpRequest,
    description: String? = null,
    extraHeaders: HttpHeaders? = null,
  ) {
    if (reportErrorsAsPlainText) {
      sendPlainText(channel, request, description, extraHeaders)
    }
    else {
      send(channel, request, description, extraHeaders)
    }
  }

  @Throws(InterruptedException::class, InvocationTargetException::class)
  protected open fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean =
    @Suppress("DEPRECATION")
    isHostTrusted(request)

  /**
   * Used to set individual API access rate limits.
   */
  @Throws(InterruptedException::class, InvocationTargetException::class)
  protected open fun getRequesterId(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Any =
    (context.channel().remoteAddress() as InetSocketAddress).address

  @Deprecated("Use {@link #isHostTrusted(FullHttpRequest, QueryStringDecoder)}")
  @Throws(InterruptedException::class, InvocationTargetException::class)
  // e.g., Upsource trust to configured host
  protected open fun isHostTrusted(request: FullHttpRequest): Boolean {
    if (service<BuiltInWebServerAuth>().isRequestSigned(request) || isOriginAllowed(request) == OriginCheckResult.ALLOW) {
      return true
    }

    val referrer = request.origin ?: request.referrer
    val host: String?
    val scheme: String?
    if (referrer.isNullOrBlank()) {
      host = null
      scheme = null
    }
    else {
      try {
        val uri = URI(referrer)
        host = uri.host.ifBlank { null }
        scheme = uri.scheme.ifBlank { null }
      }
      catch (_: URISyntaxException) {
        return false
      }
    }

    val lock = hostLocks.computeIfAbsent(host ?: "") { Object() }
    synchronized(lock) {
      if (host == null || scheme == null) {
        if (isBlockUnknownHosts) {
          return false
        }
      }
      else if (isLocalhost(host)) {
        return true
      }

      val key = if (host == null || scheme == null) null else host to scheme
      if (key != null) {
        trustedOrigins.getIfPresent(key)?.let {
          return it
        }
      }

      var isTrusted = false
      ApplicationManager.getApplication().invokeAndWait(
        {
          AppIcon.getInstance().requestAttention(null, true)
          val message = when (host) {
            null -> IdeBundle.message("warning.use.rest.api.0.and.trust.host.unknown", getServiceName())
            else -> IdeBundle.message("warning.use.rest.api.0.and.trust.host.1", getServiceName(), host)
          }
          isTrusted = showYesNoDialog(message, "title.use.rest.api")
          if (key != null) {
            trustedOrigins.put(key, isTrusted)
          }
          else if (!isTrusted) {
            isBlockUnknownHosts = showYesNoDialog(IdeBundle.message("warning.use.rest.api.block.unknown.hosts"), "title.use.rest.api")
          }
        },
        ModalityState.any(),
      )
      return isTrusted
    }
  }

  fun isHostInPredefinedHosts(request: HttpRequest, trustedPredefinedHosts: Set<String>, systemPropertyKey: String): Boolean {
    val origin = request.origin
    val originHost = if (origin == null) {
      null
    }
    else {
      try {
        URI(origin).takeIf { it.scheme == "https" }?.host?.ifBlank { null }
      }
      catch (_: URISyntaxException) {
        return false
      }
    }

    val hostName = getHostName(request)
    if (hostName != null && !isLocalhost(hostName)) {
      LOG.error("Expected 'request.hostName' to be localhost. hostName='$hostName', origin='$origin'")
    }

    return (originHost != null && (
      trustedPredefinedHosts.contains(originHost) ||
      System.getProperty(systemPropertyKey, "").splitToSequence(',').contains(originHost) ||
      isLocalhost(originHost)))
  }

  /**
   * Return error or send response using [RestService.sendOk], [RestService.send]
   */
  @Throws(IOException::class)
  abstract fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String?
}

fun HttpResponseStatus.orInSafeMode(safeStatus: HttpResponseStatus): HttpResponseStatus = when {
  Registry.`is`("ide.http.server.response.actual.status", false) || ApplicationManager.getApplication()?.isUnitTestMode == true -> this
  else -> safeStatus
}

private fun isLocalhost(hostName: @NlsSafe String): Boolean {
  return hostName.equals("localhost", ignoreCase = true) || hostName == "127.0.0.1" || hostName == "::1"
}