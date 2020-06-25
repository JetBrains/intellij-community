// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.util.io.origin
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.io.response

internal class StartUpMeasurementService : RestService() {
  override fun getServiceName() = "startUpMeasurement"

  override fun isOriginAllowed(request: HttpRequest): OriginCheckResult {
    return if (request.origin == "https://ij-perf.jetbrains.com") OriginCheckResult.ALLOW else super.isOriginAllowed(request)
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val reporter = StartUpPerformanceService.getInstance()
    val lastReport = reporter.lastReport ?: return """{"error": "Report is not ready yet, start-up in progress"}"""
    val response = response("application/json", Unpooled.wrappedBuffer(lastReport))
    sendResponse(request, context, response)
    return null
  }
}