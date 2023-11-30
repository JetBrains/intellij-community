// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.collaboration.auth.services.OAuthService
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.util.Url
import com.intellij.util.concurrency.AppExecutorUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.RestService
import org.jetbrains.io.response
import org.jetbrains.io.send
import java.util.concurrent.CompletableFuture

/**
 * The base class of the callback handler for authorization services
 */
abstract class OAuthCallbackHandlerBase : RestService() {
  protected val service: OAuthService<*> get() = oauthService()

  abstract fun oauthService(): OAuthService<*>

  override fun getServiceName(): String = service.name

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val channel = context.channel()

    val indicator = EmptyProgressIndicator()
    channel.closeFuture().addListener {
      indicator.cancel()
    }

    fun handle(indicator: EmptyProgressIndicator): AcceptCodeHandleResult? =
      ProgressManager.getInstance().runProcess(Computable {
        val oAuthResult = service.handleOAuthServerCallback(urlDecoder.path(), urlDecoder.parameters()) ?: return@Computable null
        handleOAuthResult(oAuthResult)
      }, indicator)

    val executor = AppExecutorUtil.getAppExecutorService()
    CompletableFuture.supplyAsync({ handle(indicator) }, executor).handle { res, err ->
      if (err != null) {
        if (err is ProcessCanceledException) {
          channel.close()
        }
        else {
          LOG.warn(err)
          sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, false, channel)
        }
      }
      else if (res != null) {
        when (res) {
          is AcceptCodeHandleResult.Page -> {
            response("text/html", Unpooled.wrappedBuffer(res.html.toByteArray(Charsets.UTF_8)))
              .send(context.channel(), request)
          }
          is AcceptCodeHandleResult.Redirect -> {
            sendRedirect(request, context, res.url)
          }
        }
      }
      else {
        sendStatus(HttpResponseStatus.NO_CONTENT, false, channel)
      }
    }
    return null
  }


  protected open fun handleOAuthResult(oAuthResult: OAuthService.OAuthResult<*>): AcceptCodeHandleResult {
    return handleAcceptCode(oAuthResult.isAccepted)
  }

  @Deprecated("Use handleOAuthResult instead", ReplaceWith("handleOAuthResult"))
  protected open fun handleAcceptCode(isAccepted: Boolean): AcceptCodeHandleResult {
    throw UnsupportedOperationException()
  }

  private fun sendRedirect(request: FullHttpRequest, context: ChannelHandlerContext, url: Url) {
    val headers = DefaultHttpHeaders().set(HttpHeaderNames.LOCATION, url.toExternalForm())
    HttpResponseStatus.FOUND.send(context.channel(), request, null, headers)
  }

  protected sealed class AcceptCodeHandleResult {
    class Redirect(val url: Url) : AcceptCodeHandleResult()
    class Page(val html: String) : AcceptCodeHandleResult()
  }
}
