// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.ide.AppLifecycleListener
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProviders
import com.intellij.internal.statistic.eventLog.dispatcher.ExternalUploadOrchestrator
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking


internal class EventLogApplicationLifecycleListener : AppLifecycleListener {
  override fun appWillBeClosed(isRestart: Boolean) {
    if (isRestart) {
      ExternalUploadOrchestrator.signalRestart()
    }

    // Closing every recorder's dispatcher triggers its postClose hook, which in turn calls
    // ExternalUploadOrchestrator.tryStartExternalUpload(). The orchestrator handles the legacy guards
    // (restart, run-from-sources, registry flag, enabled-recorder filter, update-in-progress) and is idempotent,
    // so we get exactly one external uploader JVM launch per IDE shutdown.
    val providers = getEventLogProviders().toList()
    if (providers.isEmpty()) return

    runWithModalProgressBlocking(ModalTaskOwner.guess(), "Starting External Log Uploader") {
      for (provider in providers) {
        val dispatcher = IntellijSensitiveDataValidator.getIfInitialized(provider.recorderId)?.reportDispatcher ?: continue
        try {
          dispatcher.close()
        }
        catch (e: Exception) {
          LOG.warn("Statistics. Failed to close report dispatcher for recorder '${provider.recorderId}'", e)
        }
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(EventLogApplicationLifecycleListener::class.java)
  }
}
