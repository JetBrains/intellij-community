// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.collaboration.auth.services.OAuthCallbackHandler
import com.intellij.collaboration.auth.services.OAuthService
import com.intellij.util.Url
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

abstract class OAuthCallbackHandlerBase : RestService() {

  protected abstract fun oauthService(): OAuthService<*>

  @Deprecated("Use handleOAuthResult instead", ReplaceWith("handleOAuthResult"))
  protected open fun handleAcceptCode(isAccepted: Boolean): AcceptCodeHandleResult {
    throw UnsupportedOperationException()
  }

  protected open fun handleOAuthResult(oAuthResult: OAuthService.OAuthResult<*>): AcceptCodeHandleResult =
    try {
      handleAcceptCode(oAuthResult.isAccepted)
    }
    catch (e: UnsupportedOperationException) {
      callbackHandler.handleAcceptCode(oAuthResult.isAccepted).let { AcceptCodeHandleResult.convertFromBase(it) }
    }

  private val callbackHandler = object : OAuthCallbackHandler() {
    override fun oauthService(): OAuthService<*> = this@OAuthCallbackHandlerBase.oauthService()

    override fun handleOAuthResult(oAuthResult: OAuthService.OAuthResult<*>): AcceptCodeHandleResult {
      return this@OAuthCallbackHandlerBase.handleOAuthResult(oAuthResult).convertToBase()
    }
  }

  override fun getServiceName(): String =
    callbackHandler.getServiceName()

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? =
    callbackHandler.execute(urlDecoder, request, context)

  protected sealed class AcceptCodeHandleResult {
    companion object {
      fun convertFromBase(baseResult: OAuthCallbackHandler.AcceptCodeHandleResult): AcceptCodeHandleResult =
        when (baseResult) {
          is OAuthCallbackHandler.AcceptCodeHandleResult.Page -> Page(baseResult.html)
          is OAuthCallbackHandler.AcceptCodeHandleResult.Redirect -> Redirect(baseResult.url)
        }
    }

    class Redirect(val url: Url) : AcceptCodeHandleResult()
    class Page(val html: String) : AcceptCodeHandleResult()

    fun convertToBase(): OAuthCallbackHandler.AcceptCodeHandleResult =
      when (this) {
        is Page -> OAuthCallbackHandler.AcceptCodeHandleResult.Page(html)
        is Redirect -> OAuthCallbackHandler.AcceptCodeHandleResult.Redirect(url)
      }
  }
}
