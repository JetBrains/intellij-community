// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.getHostName
import com.intellij.util.io.origin
import com.intellij.util.net.NetUtils
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.io.response

private val LOG = logger<StartUpMeasurementService>()

internal class StartUpMeasurementService : RestService() {
  override fun getServiceName() = "startUpMeasurement"

  override fun isAccessible(request: HttpRequest): Boolean {
    if (super.isAccessible(request)) {
      return true
    }

    // expose externally to use visualizer front-end
    // personal data is not exposed (but someone can say that 3rd plugin class names should be not exposed),
    // so, limit to dev builds only (EAP builds are not allowed too) or app in an internal mode (and still only for known hosts)
    return isTrustedHostName(request) && (ApplicationManager.getApplication().isInternal || ApplicationInfoEx.getInstanceEx().build.isSnapshot)
  }

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isTrustedHostName(request) || super.isHostTrusted(request, urlDecoder)
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val reporter = StartUpPerformanceService.getInstance()
    val lastReport = reporter.lastReport ?: return """{"error": "Report is not ready yet, start-up in progress"}"""
    val response = response("application/json", Unpooled.wrappedBuffer(lastReport))
    sendResponse(request, context, response)
    return null
  }
}

private fun isTrustedHostName(request: HttpRequest): Boolean {
  val hostName = getHostName(request) ?: return false
  if (!NetUtils.isLocalhost(hostName)) {
    LOG.error("Expected 'request.hostName' to be localhost. hostName=$hostName, origin=${request.origin}")
  }
  return hostName == "ij-perf.jetbrains.com" || hostName == "ij-perf.develar.org" || NetUtils.isLocalhost(hostName)
}