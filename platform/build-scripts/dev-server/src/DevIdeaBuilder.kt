// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package org.jetbrains.intellij.build.devServer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.ConsoleSpanExporter
import org.jetbrains.intellij.build.TracerProviderManager

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