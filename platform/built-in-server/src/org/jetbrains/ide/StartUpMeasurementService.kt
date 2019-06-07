// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.io.hostName
import com.intellij.util.net.NetUtils
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.io.response

internal class StartUpMeasurementService : RestService() {
  override fun getServiceName() = "startUpMeasurement"

  override fun isAccessible(request: HttpRequest): Boolean {
    if (super.isAccessible(request)) {
      return true
    }

    // expose externally to use visualizer front-end
    // personal data is not exposed (but someone can say that 3rd plugin class names should be not exposed),
    // so, limit to dev builds only (EAP builds are not allowed too) or app in an internal mode (and still only for known hosts)
    return isTrustedHostName(request) && (ApplicationInfoEx.getInstanceEx().build.isSnapshot || ApplicationManager.getApplication().isInternal)
  }

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isTrustedHostName(request) || super.isHostTrusted(request, urlDecoder)
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val reporter = StartupActivity.POST_STARTUP_ACTIVITY.findExtension(
      StartUpPerformanceReporter::class.java)
    if (reporter == null) {
      return "Cannot find StartUpPerformanceReporter instance"
    }

    val lastReport = reporter.lastReport ?: return "Report is not ready yet, start-up in progress"
    val response = response("application/json", Unpooled.wrappedBuffer(lastReport))
    sendResponse(request, context, response)
    return null
  }
}

private fun isTrustedHostName(request: HttpRequest): Boolean {
  val hostName = request.hostName ?: return false
  return hostName == "ij-perf.jetbrains.com" || hostName == "ij-perf.develar.org" || NetUtils.isLocalhost(hostName)
}