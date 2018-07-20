// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import java.io.File

object FeatureUsageLogger {
  private val ourLogger : FeatureUsageEventLogger

  init {
    val provider = getLoggerProvider()
    ourLogger = if (provider.isEnabled()) provider.createLogger() else FeatureUsageEmptyEventLogger()
  }

  fun log(recorderId: String, action: String) {
    return ourLogger.log(recorderId, action, false)
  }

  fun log(recorderId: String, action: String, data: Map<String, Any>) {
    return ourLogger.log(recorderId, action, data, false)
  }

  fun logState(recorderId: String, action: String) {
    return ourLogger.log(recorderId, action, true)
  }

  fun logState(recorderId: String, action: String, data: Map<String, Any>) {
    return ourLogger.log(recorderId, action, data, true)
  }

  fun getLogFiles() : List<File> {
    return ourLogger.getLogFiles()
  }

  fun isEnabled() : Boolean {
    return ourLogger !is FeatureUsageEmptyEventLogger
  }
}
