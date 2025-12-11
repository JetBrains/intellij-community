// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.util.application
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.skiko.Library

// org.jetbrains.skiko.Library.load seems expensive, better have it loaded before the first rendering happens
internal class SkikoPreloader : ApplicationActivity {
  private val SKIKO: Scope = Scope("skiko")

  @OptIn(InternalJewelApi::class)
  override suspend fun execute() {
    if (application.isHeadlessEnvironment) return

    val tracer = TelemetryManager.getInstance().getSimpleTracer(SKIKO)
    withContext(tracer.span("org.jetbrains.skiko.Library.load")) {
      val logger = thisLogger()

      val skikoPath = System.getProperty("skiko.library.path")

      logger.assertTrue(skikoPath != null || isRunningFromSources(),
                        "Skiko native libraries path 'skiko.library.path' is not set in VM Options")
      logger.debug("Preloading Skiko, skiko.library.path=$skikoPath")

      Library.load()
    }
  }
}
