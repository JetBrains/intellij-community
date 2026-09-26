// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.ide.AppLifecycleListener
import com.intellij.internal.statistic.eventLog.dispatcher.ExternalUploadOrchestrator
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking


internal class EventLogApplicationLifecycleListener : AppLifecycleListener {
  /**
   * The platform calls this before [com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector.onIdeClose].
   * So this method must not close a recorder's `FusClient`. A close also closes the SDK event queue, and every later event,
   * `ide.close` included, is then dropped. [com.intellij.internal.statistic.eventLog.StatisticsFileEventLogger.dispose]
   * flushes and closes the client after the last event.
   */
  override fun appWillBeClosed(isRestart: Boolean) {
    if (isRestart) {
      ExternalUploadOrchestrator.signalRestart()
    }

    // The orchestrator handles the legacy guards (restart, run-from-sources, registry flag, enabled-recorder filter,
    // update-in-progress) and is idempotent, so we get exactly one external uploader JVM launch per IDE shutdown.
    runWithModalProgressBlocking(ModalTaskOwner.guess(), "Starting External Log Uploader") {
      ExternalUploadOrchestrator.tryStartExternalUpload()
    }
  }
}
