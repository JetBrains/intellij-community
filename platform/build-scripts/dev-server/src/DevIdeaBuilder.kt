// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer

import com.intellij.diagnostic.telemetry.useWithScope2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.ConsoleSpanExporter
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.TracerProviderManager
import org.jetbrains.intellij.build.closeKtorClient

object DevIdeaBuilder {
  @JvmStatic
  fun main(args: Array<String>) {
    initLog()
    runBlocking(Dispatchers.Default) {
      // don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing ~500KB JSON file
      TracerProviderManager.spanExporterProvider = { listOf(ConsoleSpanExporter()) }
      try {
        buildProductInProcess(request = BuildRequest(
          homePath = getHomePath(),
          platformPrefix = System.getProperty("idea.platform.prefix") ?: "idea",
          additionalModules = getAdditionalModules()?.toList() ?: emptyList()
        ))
      }
      finally {
        TracerProviderManager.flush()
      }
    }
  }
}

suspend fun buildProductInProcess(request: BuildRequest) {
  spanBuilder("build ide").setAttribute("request", request.toString()).useWithScope2 {
    BuildServer(homePath = request.homePath, productionClassOutput = request.productionClassOutput)
      .buildProductInProcess(isServerMode = false, request = request)
    // otherwise, thread leak in tests
    if (!request.keepHttpClient) {
      closeKtorClient()
    }
  }
}