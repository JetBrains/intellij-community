// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
/**
 * Event provider for [IJFUSMapper]
 *
 * Is also handled here: [com.intellij.internal.statistic.eventLog.StatisticsEventLogProvidersHolder.isProviderApplicable]
 */
@ApiStatus.Internal
class IJMapperEventLoggerProvider : StatisticsEventLoggerProvider(
  RECORDER_ID,
  1,
  sendFrequencyMs = TimeUnit.HOURS.toMillis(1),
  maxFileSizeInBytes = 1 * 512, // enough to fill a log file with one entry of the state collector
  sendLogsOnIdeClose = true,
  isCharsEscapingRequired = false,

) {
  private val actualLogger: StatisticsEventLogger by lazy { createLogger("FUS") }

  override val logger: StatisticsEventLogger
    get() = if (isLoggingEnabled()) actualLogger else super.logger
  /**
   * Should be in sync with MLEventLoggerProvider.isRecordEnabled()
   */
  override fun isRecordEnabled(): Boolean {
    val app = ApplicationManager.getApplication()
    return !app.isUnitTestMode &&
           StatisticsUploadAssistant.isCollectAllowed() &&
           (ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct())
  }

  override fun isSendEnabled(): Boolean {
    return isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()
  }

  companion object {
    const val RECORDER_ID = "IJ_MAP"
  }
}