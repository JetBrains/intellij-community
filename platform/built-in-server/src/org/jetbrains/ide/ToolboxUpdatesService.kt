// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import org.jetbrains.io.addCommonHeaders
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

val toolboxHandlerEP: ExtensionPointName<ToolboxServiceHandler> = ExtensionPointName.create("com.intellij.toolboxServiceHandler")

interface ToolboxServiceHandler {
  /**
   * This function is executes on a background thread to handle a Toolbox
   * request. The implementation allows to send a response after a long
   * while back to Toolbox.
   *
   * The [lifetime] should be used to bind all necessary
   * resources to the underlying Toolbox connection. It can
   * be disposed during the execution of this method.
   *
   * Use the [onResult] to pass a result back to Toolbox and
   * to close the connection. The [lifetime] is disposed after
   * the connection is closed too
   */
  fun handleToolboxRequest(
    lifetime: Disposable,
    request: ToolboxActionRequest,
    onResult: (ToolboxActionResult) -> Unit,
  )
}

sealed class ToolboxActionRequest {
  data class UpdateNotification(val version: String, val build: String) : ToolboxActionRequest()
}

sealed class ToolboxActionResult {
  data class SimpleResult(val status: String) : ToolboxActionResult()
}

internal val ErrorResult = ToolboxActionResult.SimpleResult("error")

internal class ToolboxUpdatesService : RestService() {
  internal companion object {

    @Suppress("SSBasedInspection")
    private val LOG = logger<ToolboxUpdatesService>()
  }

  override fun getServiceName() = "toolbox"

  override fun isSupported(request: FullHttpRequest): Boolean {
    val token = System.getProperty("toolbox.notification.token") ?: return false
    if (request.headers()["Authorization"] != "toolbox $token") return false
    if (!request.uri().substringBefore('?').endsWith("/update-notification")) return false
    return super.isSupported(request)
  }

  override fun isMethodSupported(method: HttpMethod) = method == HttpMethod.POST

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val requestJson = createJsonReader(request).use { JsonParser.parseReader(it) }
    val channel = context.channel()

    val toolboxRequest = try {
      if (urlDecoder.path().endsWith("/update-notification")) {
        require(requestJson.isJsonObject) { "JSON Object was expected" }
        val obj = requestJson.asJsonObject

        val build = obj["build"]?.asString
        val version = obj["version"]?.asString

        require(!build.isNullOrBlank()) { "the `build` attribute must not be blank" }
        require(!version.isNullOrBlank()) { "the `version` attribute must not be blank" }

        ToolboxActionRequest.UpdateNotification(version = version, build = build)
      } else {
        sendStatus(HttpResponseStatus.NOT_FOUND, false, channel)
        return null
      }
    }
    catch (t: Throwable) {
      LOG.warn("Failed to process parameters of $request. ${t.message}", t)
      sendStatus(HttpResponseStatus.BAD_REQUEST, false, channel)
      return null
    }

    val callback = CompletableFuture<ToolboxActionResult?>()
    val lifetime = Disposer.newDisposable("toolbox-update")
    AppExecutorUtil.getAppExecutorService().submit {
      toolboxHandlerEP.forEachExtensionSafe {
        it.handleToolboxRequest(lifetime, toolboxRequest) { r -> callback.complete(r) }
      }
    }

    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
    response.addCommonHeaders()
    response.headers().remove(HttpHeaderNames.ACCEPT_RANGES)
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate") //NON-NLS
    response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
    response.headers().set(HttpHeaderNames.LAST_MODIFIED, Date(Calendar.getInstance().timeInMillis))
    channel.write(response)

    val heartbeatDelay = System.getProperty("toolbox.heartbeat.millis", "1000").toLong()
    val heartbeat = context.executor().scheduleWithFixedDelay(
      object: Runnable {
        private fun handleError(t: Throwable) {
          LOG.debug("Failed to write next heartbeat. ${t.message}", t)
          Disposer.dispose(lifetime)
        }

        private val futureListener = GenericFutureListener<Future<in Void>> { f ->
          f.cause()?.let { t -> handleError(t) }
        }

        override fun run() {
          try {
            channel
              .writeAndFlush(Unpooled.copiedBuffer(" ", Charsets.UTF_8))
              .addListener(futureListener)
          }
          catch (t: Throwable) {
            handleError(t)
          }
        }
      }, heartbeatDelay, heartbeatDelay, TimeUnit.MILLISECONDS)

    Disposer.register(lifetime) { heartbeat.cancel(false) }

    callback
      .exceptionally { e ->
        LOG.warn("The future completed with exception. ${e.message}", e)
        ErrorResult
      }
      .thenAcceptAsync(
        { result ->
          try {
            heartbeat.cancel(false)
            heartbeat.await()

            val json = JsonObject().apply {
              when (result) {
                is ToolboxActionResult.SimpleResult -> addProperty("status", result.status)
                null -> addProperty("status", "null")
              }.toString()
            }

            channel.write(Unpooled.copiedBuffer(gson.toJson(json), Charsets.UTF_8))
          }
          finally {
            runCatching {
              channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            }
            Disposer.dispose(lifetime)
          }
        },
        AppExecutorUtil.getAppExecutorService()
      )

    return null
  }
}
