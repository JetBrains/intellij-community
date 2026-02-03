// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.LogSystemCollector
import com.intellij.internal.statistic.eventLog.LogSystemCollector.failedToStartField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.notEnabledLoggerProvidersField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.restartField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.runningFromSourcesField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.sendingOnExitDisabledField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.updateInProgressField
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProviders
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking


internal class EventLogApplicationLifecycleListener : AppLifecycleListener {
  override fun appWillBeClosed(isRestart: Boolean) {
    if (isRestart) {
      LOG.info("Statistics. Don't start external uploader because there is restarted")
      LogSystemCollector.externalUploaderLaunched.log(restartField.with(true))
      return
    }

    if (isRunningFromSources()) {
      LOG.info("Statistics. Don't start external uploader because IDE is running from sources")
      LogSystemCollector.externalUploaderLaunched.log(runningFromSourcesField.with(true))
      return
    }

    if (!isSendingOnExitEnabled) {
      LOG.info("Statistics. Don't start external uploader because sending on exit is disabled")
      LogSystemCollector.externalUploaderLaunched.log(sendingOnExitDisabledField.with(true))
      return
    }

    val enabledLoggerProviders =
      getEventLogProviders().filter { p: StatisticsEventLoggerProvider? -> p!!.isSendEnabled() && p.sendLogsOnIdeClose }

    if (enabledLoggerProviders.isEmpty()) {
      LOG.info("Statistics. Don't start external uploader because there are no enabled logger providers")
      LogSystemCollector.externalUploaderLaunched.log(notEnabledLoggerProvidersField.with(true))
    }

    if (isUpdateInProgress) {
      LOG.info("Statistics. Don't start external uploader because update is in progress")
      LogSystemCollector.externalUploaderLaunched.log(updateInProgressField.with(true))
    }

    runWithModalProgressBlocking(ModalTaskOwner.guess(),
                                 "Starting External Log Uploader") {
      try {
        EventLogExternalUploader.startExternalUpload(
          enabledLoggerProviders,
          StatisticsUploadAssistant.isUseTestStatisticsConfig(),
          StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint()
        )
      }
      catch (e: Exception) {
        LOG.error("Statistics. Failed to start external log uploader", e)
        LogSystemCollector.externalUploaderLaunched.log(failedToStartField.with(true))
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(EventLogApplicationLifecycleListener::class.java)

    private val isSendingOnExitEnabled: Boolean
      get() = // the default value is true, but if a registry is yet not loaded on appWillBeClosed, it means that something bad happened
        Registry.`is`("feature.usage.event.log.send.on.ide.close", false)

    private val isUpdateInProgress: Boolean
      get() = ApplicationInfo.getInstance().getBuild().asString() == PropertiesComponent.getInstance().getValue("ide.self.update.started.for.build")
  }
}