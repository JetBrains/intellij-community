// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.SpecialConfigFiles
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.referrer
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.cookie.DefaultCookie
import io.netty.handler.codec.http.cookie.ServerCookieDecoder
import io.netty.handler.codec.http.cookie.ServerCookieEncoder
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.fileAttributesViewOrNull
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Service
class BuiltInWebServerAuth {
  private val STANDARD_COOKIE by lazy {
    val configPath = PathManager.getConfigPath()
    val file = Path.of(configPath, SpecialConfigFiles.USER_WEB_TOKEN)
    var token: String? = null
    if (file.exists()) {
      try {
        token = UUID.fromString(file.readText()).toString()
      }
      catch (e: Exception) {
        logger<BuiltInWebServerAuth>().warn(e)
      }
    }
    if (token == null) {
      token = UUID.randomUUID().toString()
      file.writeText(token)
      file.fileAttributesViewOrNull<PosixFileAttributeView>()
        ?.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    }

    // explicit setting domain cookie on localhost doesn't work for chrome
    // http://stackoverflow.com/questions/8134384/chrome-doesnt-create-cookie-for-domain-localhost-in-broken-https
    val productName = ApplicationNamesInfo.getInstance().lowercaseProductName
    val cookie = DefaultCookie("${productName}-${Integer.toHexString(configPath.hashCode())}", token)
    cookie.isHttpOnly = true
    cookie.setMaxAge(TimeUnit.DAYS.toSeconds(365 * 10))
    cookie.setPath("/")
    cookie
  }

  // expire after access because we reuse tokens
  private val tokens = Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build<String, Boolean>()

  fun acquireToken(): String {
    var token = tokens.asMap().keys.firstOrNull()
    if (token == null) {
      token = DigestUtil.randomToken()
      tokens.put(token, true)
    } else {
      // Update token's access time
      tokens.getIfPresent(token)
    }
    return token
  }

  internal fun validateToken(request: HttpRequest): HttpHeaders? {
    if (BuiltInServerOptions.getInstance().allowUnsignedRequests) {
      return EmptyHttpHeaders.INSTANCE
    }

    request.headers().get(HttpHeaderNames.COOKIE)?.let {
      for (cookie in ServerCookieDecoder.STRICT.decode(it)) {
        if (cookie.name() == STANDARD_COOKIE.name()) {
          if (cookie.value() == STANDARD_COOKIE.value()) {
            return EmptyHttpHeaders.INSTANCE
          }
          break
        }
      }
    }

    if (isRequestSigned(request)) {
      return DefaultHttpHeaders().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(STANDARD_COOKIE) + "; SameSite=strict")
    }

    return null
  }

  internal fun isRequestSigned(request: HttpRequest): Boolean {
    if (BuiltInServerOptions.getInstance().allowUnsignedRequests) {
      return true
    }

    // we must check the referrer - if HTML is cached, a browser will send a request without a query
    val token =
      request.headers().get(TOKEN_HEADER_NAME)
      ?: QueryStringDecoder(request.uri()).parameters()[TOKEN_PARAM_NAME]?.firstOrNull()
      ?: request.referrer?.let { QueryStringDecoder(it).parameters()[TOKEN_PARAM_NAME]?.firstOrNull() }

    // we don't invalidate the token, allowing making further requests with it (required for `DocumentationComponent`)
    return token != null && tokens.getIfPresent(token) != null
  }
}
