// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.setSkikoLibraryPath
import org.jetbrains.skiko.Library

// org.jetbrains.skiko.Library.load seems expensive, better have it loaded before the first rendering happens
internal class SkikoPreloader : ApplicationActivity {
  private val SKIKO: Scope = Scope("skiko")

  override suspend fun execute() {
    val tracer = TelemetryManager.getInstance().getSimpleTracer(SKIKO)
    withContext(tracer.span("org.jetbrains.skiko.Library.load")) {
      thisLogger().debug("Preloading Skiko")

      setSkikoLibraryPath()
      Library.load()
    }
  }
}
