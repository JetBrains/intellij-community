// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.collaboration.auth.services.OAuthService
import com.intellij.util.Url
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.RestService
import org.jetbrains.io.response
import org.jetbrains.io.send

/**
 * The base class of the callback handler for authorization services
 */
abstract class OAuthCallbackHandlerBase : RestService() {
  protected val service: OAuthService<*> get() = oauthService()

  abstract fun oauthService(): OAuthService<*>

  override fun getServiceName(): String = service.name

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val isCodeAccepted = service.handleServerCallback(urlDecoder.path(), urlDecoder.parameters())

    when (val handleResult = handleAcceptCode(isCodeAccepted)) {
      is AcceptCodeHandleResult.Page -> {
        response(
          "text/html",
          Unpooled.wrappedBuffer(handleResult.html.toByteArray(Charsets.UTF_8))
        ).send(context.channel(), request)
      }
      is AcceptCodeHandleResult.Redirect -> sendRedirect(request, context, handleResult.url)
    }

    return null
  }

  protected abstract fun handleAcceptCode(isAccepted: Boolean): AcceptCodeHandleResult

  private fun sendRedirect(request: FullHttpRequest, context: ChannelHandlerContext, url: Url) {
    val headers = DefaultHttpHeaders().set(HttpHeaderNames.LOCATION, url.toExternalForm())
    HttpResponseStatus.FOUND.send(context.channel(), request, null, headers)
  }

  protected sealed class AcceptCodeHandleResult {
    class Redirect(val url: Url) : AcceptCodeHandleResult()
    class Page(val html: String) : AcceptCodeHandleResult()
  }
}
