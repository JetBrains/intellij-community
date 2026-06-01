// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.diagnostic.ThreadDump
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

@ApiStatus.Internal
interface UnhandledReportSinkService {
  companion object {
    @JvmStatic
    fun getInstance(): UnhandledReportSinkService? = try {
      serviceOrNull()
    }
    catch (_: CancellationException) {
      null  // the application is already disposed
    }
  }

  fun report(data: PluginFreezeReportData)
  fun report(data: PluginExceptionReportData)

  @ApiStatus.Internal
  class PluginExceptionReportData(
    val pluginId: PluginId,
    val t: Throwable,
  )

  @ApiStatus.Internal
  class PluginFreezeReportData(
    val pluginId: PluginId,
    val message: String,
    val durationMs: Long,
    val attachments: List<Attachment>,
    val threadDumps: Collection<ThreadDump>,
  )
}
