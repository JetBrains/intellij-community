// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.ide.plugins.PluginManagerCore.isUpdatedBundledPlugin
import com.intellij.openapi.diagnostic.ErrorReportSink
import com.intellij.openapi.diagnostic.ErrorReportSinkBean
import com.intellij.openapi.diagnostic.UnhandledErrorReport
import com.intellij.openapi.diagnostic.UnhandledExceptionReport
import com.intellij.openapi.diagnostic.UnhandledFreezeReport
import com.intellij.openapi.diagnostic.UnhandledReportSinkService
import com.intellij.openapi.diagnostic.UnhandledReportSinkService.PluginExceptionReportData
import com.intellij.openapi.diagnostic.UnhandledReportSinkService.PluginFreezeReportData
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val MAX_EXCEPTIONS_PER_PLUGIN = 10_000

internal class UnhandledReportSinkServiceImpl(coroutineScope: CoroutineScope) : UnhandledReportSinkService {
  private val flow = MutableSharedFlow<Pair<ErrorReportSink, UnhandledErrorReport>>(
    extraBufferCapacity = 100,
    onBufferOverflow = BufferOverflow.DROP_LATEST,
  )
  private val exceptionCountPerPlugin = ConcurrentHashMap<PluginId, Int>()

  init {
    coroutineScope.launch(Dispatchers.IO) {
      flow.collect { (sink, report) ->
        try {
          sink.submit(report)
        }
        catch (e: Exception) {
          thisLogger().warn("Failed to submit an unhandled error report to ${sink.javaClass}", e)
        }
      }
    }
  }

  override fun report(data: PluginFreezeReportData) {
    if (isPluginFromDistribution(data.pluginId)) return

    emitReport(data.pluginId, UnhandledFreezeReport(data.message, data.durationMs, data.attachments, data.threadDumps))
  }

  override fun report(data: PluginExceptionReportData) {
    if (isPluginFromDistribution(data.pluginId)) return

    val count = exceptionCountPerPlugin.merge(data.pluginId, 1, Int::plus) ?: return
    if (count > MAX_EXCEPTIONS_PER_PLUGIN) return
    if (count == MAX_EXCEPTIONS_PER_PLUGIN) {
      thisLogger().warn("Exception report limit reached for plugin ${data.pluginId}; further reports will be dropped")
    }

    emitReport(data.pluginId, UnhandledExceptionReport(data.t))
  }

  private fun emitReport(pluginId: PluginId, report: UnhandledErrorReport) {
    ErrorReportSinkBean.EP_NAME.extensionList
      .firstOrNull { it.pluginDescriptor.pluginId == pluginId }
      ?.let { flow.tryEmit(Pair(it.instance, report)) }
  }

  private fun isPluginFromDistribution(pluginId: PluginId?): Boolean {
    val descriptor = getPlugin(pluginId)
    return descriptor != null && (descriptor.isBundled || isUpdatedBundledPlugin(descriptor))
  }
}
