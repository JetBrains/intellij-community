// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer

import com.intellij.diagnostic.telemetry.useWithScope2
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.ConsoleSpanExporter
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.TracerProviderManager
import java.nio.file.Path
import java.util.function.Supplier

object DevIdeaBuilder {
  @JvmStatic
  fun main(args: Array<String>) {
    initLog()
    runBlocking(Dispatchers.Default) {
      // don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing ~500KB JSON file
      TracerProviderManager.spanExporterProvider = Supplier { listOf(ConsoleSpanExporter()) }
      try {
        buildProductInProcess(homePath = getHomePath(),
                              platformPrefix = System.getProperty("idea.platform.prefix") ?: "idea",
                              additionalModules = getAdditionalModules()?.toList() ?: emptyList())
      }
      finally {
        TracerProviderManager.flush()
      }
    }
  }
}

suspend fun buildProductInProcess(homePath: Path, platformPrefix: String, additionalModules: List<String>) {
  spanBuilder("build ide")
    .setAttribute("platformPrefix", platformPrefix)
    .setAttribute(AttributeKey.stringArrayKey("additionalModules"), additionalModules)
    .useWithScope2 {
      BuildServer(homePath = homePath, additionalModules = additionalModules)
        .buildProductInProcess(platformPrefix, isServerMode = false)
    }
}