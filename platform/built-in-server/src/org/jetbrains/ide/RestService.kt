// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.stream.MalformedJsonException
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.showYesNoDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AppIcon
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.origin
import com.intellij.util.io.referrer
import com.intellij.util.net.NetUtils
import com.intellij.util.text.nullize
import com.intellij.xml.util.XmlStringUtil
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.annotations.NonNls
import org.jetbrains.builtInWebServer.isSignedRequest
import org.jetbrains.io.addCommonHeaders
import org.jetbrains.io.addNoCache
import org.jetbrains.io.response
import org.jetbrains.io.send
import java.awt.Window
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Document your service using [apiDoc](http://apidocjs.com).
 * To extract a big example from source code, consider adding a *.coffee file near the sources
 * (or Python/Ruby, but CoffeeScript is recommended because it's plugin is lightweight).
 * See [AboutHttpService] for example.
 *
 * Don't create [JsonReader]/[JsonWriter] directly, use only provided [createJsonReader] and [createJsonWriter] methods
 * (to ensure that you handle in/out according to REST API guidelines).
 *
 * @see <a href="http://www.vinaysahni.com/best-practices-for-a-pragmatic-restful-api">Best Practices for Designing a Pragmatic REST API</a>.
 */
@Suppress("HardCodedStringLiteral")
abstract class RestService : HttpRequestHandler() {
  companion object {
    @JvmField
    val LOG = logger<RestService>()

    const val PREFIX = "api"

    @JvmStatic
    fun activateLastFocusedFrame() {
      (IdeFocusManager.getGlobalInstance().lastFocusedFrame as? Window)?.toFront()
    }

    @JvmStatic
    fun createJsonReader(request: FullHttpRequest): JsonReader {
      val reader = JsonReader(ByteBufInputStream(request.content()).reader())
      reader.isLenient = true
      return reader
    }

    @JvmStatic
    fun createJsonWriter(out: OutputStream): JsonWriter {
      val writer = JsonWriter(out.writer())
      writer.setIndent("  ")
      return writer
    }

    @JvmStatic
    fun getLastFocusedOrOpenedProject(): Project? {
      return IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: ProjectManager.getInstance().openProjects.firstOrNull()
    }

    @JvmStatic
    fun sendOk(request: FullHttpRequest, context: ChannelHandlerContext) {
      sendStatus(HttpResponseStatus.OK, HttpUtil.isKeepAlive(request), context.channel())
    }

    @JvmStatic
    fun sendStatus(status: HttpResponseStatus, keepAlive: Boolean, channel: Channel) {
      val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status)
      HttpUtil.setContentLength(response, 0)
      response.addCommonHeaders()
      response.addNoCache()
      if (keepAlive) {
        HttpUtil.setKeepAlive(response, true)
      }
      response.headers().set("X-Frame-Options", "Deny")
      response.send(channel, !keepAlive)
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
    fun getStringParameter(name: String, urlDecoder: QueryStringDecoder): String? {
      return urlDecoder.parameters()[name]?.lastOrNull()
    }

    @JvmStatic
    fun getIntParameter(name: String, urlDecoder: QueryStringDecoder): Int {
      return StringUtilRt.parseInt(getStringParameter(name, urlDecoder).nullize(nullizeSpaces = true), -1)
    }

    @JvmOverloads
    @JvmStatic
    fun getBooleanParameter(name: String, urlDecoder: QueryStringDecoder, defaultValue: Boolean = false): Boolean {
      val values = urlDecoder.parameters()[name] ?: return defaultValue
      // if just name specified, so, true
      val value = values.lastOrNull() ?: return true
      return value.toBoolean()
    }

    fun parameterMissedErrorMessage(name: String) = "Parameter \"$name\" is not specified"
  }

  protected val gson: Gson by lazy {
    GsonBuilder()
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .create()
  }

  private val abuseCounter = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build<InetAddress, AtomicInteger>(CacheLoader { AtomicInteger() })

  private val trustedOrigins = Caffeine.newBuilder()
    .maximumSize(1024)
    .expireAfterWrite(1, TimeUnit.DAYS)
    .build<String, Boolean>()
  private val hostLocks = ContainerUtil.createConcurrentWeakKeyWeakValueMap<String, Any>()

  private var isBlockUnknownHosts = false

  /**
   * Service url must be "/api/$serviceName", but to preserve backward compatibility, prefixless path could be also supported
   */
  protected open val isPrefixlessAllowed: Boolean
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

  protected open fun isMethodSupported(method: HttpMethod): Boolean {
    return method === HttpMethod.GET
  }

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    try {
      val counter = abuseCounter.get((context.channel().remoteAddress() as InetSocketAddress).address)!!
      if (counter.incrementAndGet() > Registry.intValue("ide.rest.api.requests.per.minute", 30)) {
        HttpResponseStatus.TOO_MANY_REQUESTS.orInSafeMode(HttpResponseStatus.OK).send(context.channel(), request)
        return true
      }

      if (!isHostTrusted(request, urlDecoder)) {
        HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.OK).send(context.channel(), request)
        return true
      }

      val error = execute(urlDecoder, request, context)
      if (error != null) {
        HttpResponseStatus.BAD_REQUEST.send(context.channel(), request, error)
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
      status.send(context.channel(), request, XmlStringUtil.escapeString(ExceptionUtil.getThrowableText(e)))
    }

    return true
  }

  @Throws(InterruptedException::class, InvocationTargetException::class)
  protected open fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    @Suppress("DEPRECATION")
    return isHostTrusted(request)
  }

  @Deprecated("Use {@link #isHostTrusted(FullHttpRequest, QueryStringDecoder)}")
  @Throws(InterruptedException::class, InvocationTargetException::class)
  // e.g. upsource trust to configured host
  protected open fun isHostTrusted(request: FullHttpRequest): Boolean {
    if (request.isSignedRequest() || isOriginAllowed(request) == OriginCheckResult.ALLOW) {
      return true
    }

    val referrer = request.origin ?: request.referrer
    val host = try {
      if (referrer == null) null else URI(referrer).host.nullize()
    }
    catch (ignored: URISyntaxException) {
      return false
    }

    val lock = hostLocks.computeIfAbsent(host ?: "") { Object() }
    synchronized(lock) {
      if (host != null) {
        if (NetUtils.isLocalhost(host)) {
          return true
        }
        else {
          trustedOrigins.getIfPresent(host)?.let {
            return it
          }
        }
      }
      else {
        if (isBlockUnknownHosts) return false
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
          if (host != null) {
            trustedOrigins.put(host, isTrusted)
          }
          else {
            if (!isTrusted) {
              isBlockUnknownHosts = showYesNoDialog(IdeBundle.message("warning.use.rest.api.block.unknown.hosts"), "title.use.rest.api")
            }
          }
        }, ModalityState.any())
      return isTrusted
    }
  }

  /**
   * Return error or send response using [sendOk], [send]
   */
  @Throws(IOException::class)
  @NonNls
  abstract fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String?
}

internal fun HttpResponseStatus.orInSafeMode(safeStatus: HttpResponseStatus): HttpResponseStatus {
  return if (Registry.`is`("ide.http.server.response.actual.status", true) || ApplicationManager.getApplication()?.isUnitTestMode == true) this else safeStatus
}
