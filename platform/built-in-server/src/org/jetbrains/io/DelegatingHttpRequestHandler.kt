// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.isWriteFromBrowserWithoutOrigin
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.util.AttributeKey
import org.jetbrains.ide.HttpRequestHandler
import java.lang.ref.WeakReference

private val PREV_HANDLER = AttributeKey.valueOf<WeakReference<HttpRequestHandler>>("DelegatingHttpRequestHandler.handler")

@ChannelHandler.Sharable
internal class DelegatingHttpRequestHandler() : SimpleChannelInboundHandlerAdapter<FullHttpRequest>() {
  override fun messageReceived(context: ChannelHandlerContext, request: FullHttpRequest) {
    logger<BuiltInServer>().debug { "\n\nIN HTTP: $request\n\n" }

    if (!process(context, request, QueryStringDecoder(request.uri()))) {
      createStatusResponse(HttpResponseStatus.NOT_FOUND, request).send(context.channel(), request)
    }
  }

  private fun process(context: ChannelHandlerContext, request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    if (request.isWriteFromBrowserWithoutOrigin()) {
      return false
    }

    fun HttpRequestHandler.checkAndProcess(): Boolean =
      isSupported(request) && isAccessible(request) && process(urlDecoder, request, context)

    val prevHandlerAttribute = context.channel().attr(PREV_HANDLER)

    val prevHandler = prevHandlerAttribute.get()?.get()
    if (prevHandler != null) {
      if (prevHandler.checkAndProcess()) {
        return true
      }
      // the cached handler is not suitable for this request, so let's find it again
      prevHandlerAttribute.set(null)
    }

    val handler = HttpRequestHandler.EP_NAME.findFirstSafe { it.checkAndProcess() }
    if (handler != null) {
      prevHandlerAttribute.set(WeakReference(handler))
      return true
    }

    return false
  }

  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    try {
      context.channel().attr(PREV_HANDLER).set(null)
    }
    finally {
      NettyUtil.logAndClose(cause, logger<BuiltInServer>(), context.channel())
    }
  }
}
