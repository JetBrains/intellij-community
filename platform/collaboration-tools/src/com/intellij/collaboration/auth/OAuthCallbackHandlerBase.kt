// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.collaboration.auth.services.OAuthService
import com.intellij.util.Url
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.RestService
import org.jetbrains.io.send

/**
 * The base class of the callback handler for authorization services
 */
abstract class OAuthCallbackHandlerBase : RestService() {
  companion object {
    private const val INVALID_REQUEST_ERROR = "Invalid Request"
  }

  protected val service: OAuthService<*> get() = oauthService()
  abstract fun oauthService(): OAuthService<*>

  private val QueryStringDecoder.isAuthorizationCodeUrl: Boolean get() = path() == service.authorizationCodeUrl.path
  private val QueryStringDecoder.authorizationCode: String? get() = parameters()["code"]?.firstOrNull()

  override fun getServiceName(): String = service.name

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    if (!urlDecoder.isAuthorizationCodeUrl) return INVALID_REQUEST_ERROR
    val code = urlDecoder.authorizationCode ?: return INVALID_REQUEST_ERROR

    val isCodeAccepted = service.acceptCode(code)
    val redirectUrl = if (isCodeAccepted) service.successRedirectUrl else service.errorRedirectUrl

    sendRedirect(request, context, redirectUrl)
    return null
  }

  private fun sendRedirect(request: FullHttpRequest, context: ChannelHandlerContext, url: Url) {
    val headers = DefaultHttpHeaders().set(HttpHeaderNames.LOCATION, url.toExternalForm())
    HttpResponseStatus.FOUND.send(context.channel(), request, null, headers)
  }
}
