// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import java.awt.GraphicsEnvironment

@ApiStatus.Internal
data class PluginLoadingErrorReportingPolicy(
  val logLevel: PluginLoadingErrorLogLevel,
  val reportToUser: Boolean,
) {
  companion object {
    fun forCurrentProduct(): PluginLoadingErrorReportingPolicy = product(
      isUnitTestMode = PluginManagerCore.isUnitTestMode,
      isHeadless = GraphicsEnvironment.isHeadless(),
      isFleetBackend = PlatformUtils.isFleetBackend(),
    )

    fun product(isUnitTestMode: Boolean, isHeadless: Boolean, isFleetBackend: Boolean): PluginLoadingErrorReportingPolicy = when {
      isUnitTestMode -> PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true)
      !isHeadless -> PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.WARN, reportToUser = true)
      isFleetBackend -> PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.WARN, reportToUser = false)
      else -> PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.ERROR, reportToUser = false)
    }
  }
}

@ApiStatus.Internal
enum class PluginLoadingErrorLogLevel { INFO, WARN, ERROR }
