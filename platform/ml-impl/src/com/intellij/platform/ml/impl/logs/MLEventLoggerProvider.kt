// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit

/**
 * Creates collector of ML logs
 *
 * Is also handled here: [com.intellij.internal.statistic.eventLog.StatisticsEventLogProvidersHolder.isProviderApplicable]
 */
@ApiStatus.Internal
class MLEventLoggerProvider : StatisticsEventLoggerProvider(
  ML_RECORDER_ID,
  1,
  sendFrequencyMs = TimeUnit.MINUTES.toMillis(10),
  maxFileSizeInBytes = 100 * 1024,
  sendLogsOnIdeClose = true,
  isCharsEscapingRequired = false,
  useDefaultRecorderId = true
) {

  /**
   * Should be in sync with IJMapperEventLoggerProvider.isRecordEnabled()
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
    const val ML_RECORDER_ID = "ML"
  }
}
