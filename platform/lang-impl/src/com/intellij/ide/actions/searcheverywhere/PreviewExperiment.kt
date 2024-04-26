// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PlatformUtils

object PreviewExperiment {
  val isExperimentEnabled = StatisticsUploadAssistant.isSendAllowed()
                            && ApplicationManager.getApplication().isEAP
                            && EventLogConfiguration.getInstance().bucket < 128
                            && !PlatformUtils.isRider() // RIDER-105221
                            && !PlatformUtils.isCLion() // CPP-37368
}