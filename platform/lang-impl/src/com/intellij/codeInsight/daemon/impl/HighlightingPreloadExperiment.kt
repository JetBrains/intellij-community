// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

private const val GROUPS = 4

@ApiStatus.Internal
class HighlightingPreloadExperiment {
  val isExperimentEnabled = StatisticsUploadAssistant.isSendAllowed()
                            && ApplicationManager.getApplication().isEAP
                            && EventLogConfiguration.getInstance().bucket % GROUPS == 0
}