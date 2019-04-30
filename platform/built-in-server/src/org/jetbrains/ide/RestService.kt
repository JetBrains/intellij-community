// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.google.common.base.Supplier
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.stream.MalformedJsonException
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.showYesNoDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AppIcon
import com.intellij.util.ExceptionUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.origin
import com.intellij.util.io.referrer
import com.intellij.util.net.NetUtils
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.isSignedRequest
import org.jetbrains.io.*
import java.awt.Window
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Document your service using [apiDoc](http://apidocjs.com). To extract big example from source code, consider to use *.coffee file near your source file.
 * (or Python/Ruby, but coffee recommended because it's plugin is lightweight). See [AboutHttpService] for example.
 *
 * Don't create JsonReader/JsonWriter directly, use only provided [.createJsonReader], [.createJsonWriter] methods (to ensure that you handle in/out according to REST API guidelines).
 *
 * @see [Best Practices for Designing a Pragmatic RESTful API](http://www.vinaysahni.com/best-practices-for-a-pragmatic-restful-api).
 */
abstract class RestService : HttpRequestHandler() {
  companion object {
    @JvmField
    val LOG = Logger.getInstance(RestService::class.java)

    const val PREFIX = "api"

    @JvmStatic
    protected fun activateLastFocusedFrame() {
      val frame = IdeFocusManager.getGlobalInstance().lastFocusedFrame
      if (frame is Window) {
        (frame as Window).toFront()
      }
    }

    @JvmStatic
    protected fun createJsonReader(request: FullHttpRequest): JsonReader {
      val reader = JsonReader(InputStreamReader(ByteBufInputStream(request.content()), StandardCharsets.UTF_8))
      reader.isLenient = true
      return reader
    }

    @JvmStatic
    protected fun createJsonWriter(out: OutputStream): JsonWriter {
      val writer = JsonWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
      writer.setIndent("  ")
      return writer
    }

    @JvmStatic
    fun getLastFocusedOrOpenedProject(): Project? {
      val lastFocusedFrame = IdeFocusManager.getGlobalInstance().lastFocusedFrame
      val project = lastFocusedFrame?.project
      if (project == null) {
        val openProjects = ProjectManager.getInstance().openProjects
        return if (openProjects.size > 0) openProjects[0] else null
      }
      return project
    }

    @JvmStatic
    protected fun sendOk(request: FullHttpRequest, context: ChannelHandlerContext) {
      sendStatus(HttpResponseStatus.OK, HttpUtil.isKeepAlive(request), context.channel())
    }

    @JvmStatic
    protected fun sendStatus(status: HttpResponseStatus, keepAlive: Boolean, channel: Channel) {
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
    protected fun send(byteOut: BufferExposingByteArrayOutputStream, request: HttpRequest, context: ChannelHandlerContext) {
      val response = response("application/json", Unpooled.wrappedBuffer(byteOut.internalBuffer, 0, byteOut.size()))
      sendResponse(request, context, response)
    }

    @JvmStatic
    protected fun sendResponse(request: HttpRequest, context: ChannelHandlerContext, response: HttpResponse) {
      response.addNoCache()
      response.headers().set("X-Frame-Options", "Deny")
      response.send(context.channel(), request)
    }

    @JvmStatic
    protected fun getStringParameter(name: String, urlDecoder: QueryStringDecoder): String? {
      return ContainerUtil.getLastItem(urlDecoder.parameters()[name])
    }

    @JvmStatic
    protected fun getIntParameter(name: String, urlDecoder: QueryStringDecoder): Int {
      return StringUtilRt.parseInt(StringUtil.nullize(ContainerUtil.getLastItem(urlDecoder.parameters()[name]), true), -1)
    }

    @JvmOverloads
    @JvmStatic
    protected fun getBooleanParameter(name: String, urlDecoder: QueryStringDecoder, defaultValue: Boolean = false): Boolean {
      val values = urlDecoder.parameters()[name]
      if (ContainerUtil.isEmpty(values)) {
        return defaultValue
      }

      val value = values!!.get(values.size - 1)
      // if just name specified, so, true
      return value.isEmpty() || java.lang.Boolean.parseBoolean(value)
    }
  }

  protected val gson: NotNullLazyValue<Gson> = object : NotNullLazyValue<Gson>() {
    override fun compute(): Gson {
      return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    }
  }

  private val abuseCounter = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build<InetAddress, AtomicInteger>(
    CacheLoader.from(Supplier { AtomicInteger() }))

  private val trustedOrigins = CacheBuilder.newBuilder().maximumSize(1024).expireAfterWrite(1, TimeUnit.DAYS).build<String, Boolean>()

  /**
   * Service url must be "/api/$serviceName", but to preserve backward compatibility, prefixless path could be also supported
   */
  protected open val isPrefixlessAllowed: Boolean
    get() = false

  /**
   * Use human-readable name or UUID if it is an internal service.
   */
  protected abstract fun getServiceName(): String

  override fun isSupported(request: FullHttpRequest): Boolean {
    if (!isMethodSupported(request.method())) {
      return false
    }

    val uri = request.uri()

    if (isPrefixlessAllowed && HttpRequestHandler.checkPrefix(uri, getServiceName())) {
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
      val counter = abuseCounter.get((context.channel().remoteAddress() as InetSocketAddress).address)
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
      status.send(context.channel(), request, ExceptionUtil.getThrowableText(e))
    }

    return true
  }

  @Throws(InterruptedException::class, InvocationTargetException::class)
  protected open fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isHostTrusted(request)
  }

  @Deprecated("Use {@link #isHostTrusted(FullHttpRequest, QueryStringDecoder)}")
  @Throws(InterruptedException::class, InvocationTargetException::class)
  // e.g. upsource trust to configured host
  protected open fun isHostTrusted(request: FullHttpRequest): Boolean {
    if (request.isSignedRequest()) {
      return true
    }

    var referrer = request.origin
    if (referrer == null) {
      referrer = request.referrer
    }

    val host: String?
    try {
      host = StringUtil.nullize(if (referrer == null) null else URI(referrer).host)
    }
    catch (ignored: URISyntaxException) {
      return false
    }

    val isTrusted = Ref.create<Boolean>()
    if (host != null) {
      if (NetUtils.isLocalhost(host)) {
        isTrusted.set(true)
      }
      else {
        isTrusted.set(trustedOrigins.getIfPresent(host))
      }
    }

    if (isTrusted.isNull) {
      ApplicationManager.getApplication().invokeAndWait({
                                                          AppIcon.getInstance().requestAttention(null, true)
                                                          isTrusted.set(showYesNoDialog(
                                                            IdeBundle.message("warning.use.rest.api", getServiceName(),
                                                                              ObjectUtils.chooseNotNull(host, "unknown host")),
                                                            "title.use.rest.api"))
                                                          if (host != null) {
                                                            trustedOrigins.put(host, isTrusted.get())
                                                          }
                                                        }, ModalityState.any())
    }
    return isTrusted.get()
  }

  /**
   * Return error or send response using [.sendOk], [.send]
   */
  @Throws(IOException::class)
  abstract fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String?
}
