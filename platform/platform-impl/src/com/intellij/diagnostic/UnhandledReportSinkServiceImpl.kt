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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal class UnhandledReportSinkServiceImpl(coroutineScope: CoroutineScope) : UnhandledReportSinkService {
  private val flow = MutableSharedFlow<Pair<ErrorReportSink, UnhandledErrorReport>>()

  init {
    coroutineScope.launch(Dispatchers.IO) {
      flow.collect { (sink, report) ->
        try {
          sink.submit(report)
        }
        catch (e: Exception) {
          logger<UnhandledErrorReport>().warn("Failed to submit unhandled error report to " + sink.javaClass, e)
        }
      }
    }
  }

  override fun report(data: PluginFreezeReportData) {
    if (isPluginFromDistribution(data.pluginId)) return

    for (it in ErrorReportSinkBean.EP_NAME.extensionList) {
      if (it.pluginDescriptor.pluginId == data.pluginId) {
        flow.tryEmit(Pair(it.instance, UnhandledFreezeReport(data.message, data.durationMs, data.attachments, data.threadDumps)))
      }
    }
  }

  override fun report(data: PluginExceptionReportData) {
    if (isPluginFromDistribution(data.pluginId)) return

    for (it in ErrorReportSinkBean.EP_NAME.extensionList) {
      if (it.pluginDescriptor.pluginId == data.pluginId) {
        flow.tryEmit(Pair(it.instance, UnhandledExceptionReport(data.t)))
      }
    }
  }

  private fun isPluginFromDistribution(pluginId: PluginId?): Boolean {
    val descriptor = getPlugin(pluginId)
    return descriptor != null && (descriptor.isBundled || isUpdatedBundledPlugin(descriptor))
  }
}