// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.ide.plugins.ProductLoadingStrategy
import com.intellij.idea.AppMode
import com.intellij.internal.statistic.eventLog.EventLogConfigOptionsService
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.StatisticsEventLogFileWriter
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.StatisticsFileEventLogger
import com.intellij.internal.statistic.eventLog.logger.StatisticsEventLogThrottleWriter
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit

val maxFileSizeInBytes = 100 * 1024

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
  maxFileSizeInBytes = maxFileSizeInBytes,
  sendLogsOnIdeClose = true,
  isCharsEscapingRequired = false
) {

  private val actualLogger: StatisticsEventLogger by lazy { createLmLogger() }

  override val logger: StatisticsEventLogger
    get() = if (isLoggingEnabled()) actualLogger else super.logger

  // copied from the base implementation to avoid API changes.
  private fun createLmLogger(): StatisticsEventLogger {
    val app = ApplicationManager.getApplication()
    val isEap = app != null && app.isEAP
    val isHeadless = app != null && app.isHeadlessEnvironment
    // Use `String?` instead of boolean flag for future expansion with other IDE modes
    val ideMode = if(AppMode.isRemoteDevHost()) "RDH" else null
    val currentProductModeId = ProductLoadingStrategy.strategy.currentModeId
    val productMode = if (currentProductModeId != ProductMode.MONOLITH.id) {
      currentProductModeId
    } else if (detectClionNova()) {
      "nova"
    } else {
      null
    }
    val eventLogConfiguration = EventLogConfiguration.getInstance()
    val config = eventLogConfiguration.getOrCreate(recorderId, "FUS")
    val writer = StatisticsEventLogFileWriter(recorderId, this, maxFileSizeInBytes, isEap, eventLogConfiguration.build)

    val configService = EventLogConfigOptionsService.getInstance()
    val throttledWriter = StatisticsEventLogThrottleWriter(
      configService, recorderId, version.toString(), writer, coroutineScope
    )

    val logger = StatisticsFileEventLogger(
      recorderId, config.sessionId, isHeadless, eventLogConfiguration.build, config.bucket.toString(), version.toString(),
      throttledWriter, UsageStatisticsPersistenceComponent.getInstance(), createEventsMergeStrategy(), ideMode, productMode
    )

    coroutineScope.coroutineContext.job.invokeOnCompletion { Disposer.dispose(logger) }
    return logger
  }

  private fun detectClionNova(): Boolean {
    return System.getProperty("idea.suppressed.plugins.set.selector") == "radler"
  }


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
