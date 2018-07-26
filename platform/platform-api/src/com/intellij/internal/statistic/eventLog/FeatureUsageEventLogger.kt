// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.diagnostic.Logger

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import java.io.File
import java.util.*

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.FeatureUsageEventLogger")
private val EP_NAME = ExtensionPointName.create<FeatureUsageEventLoggerProvider>("com.intellij.statistic.eventLog.fusEventLoggerProvider")

interface FeatureUsageEventLogger {

  fun log(recorderId: String, action: String, isState: Boolean)

  fun log(recorderId: String, action: String, data: Map<String, Any>, isState: Boolean)

  fun getLogFiles(): List<File>

}

interface FeatureUsageEventLoggerProvider {
  fun isEnabled() : Boolean

  fun createLogger() : FeatureUsageEventLogger
}

class FeatureUsageEmptyEventLoggerProvider : FeatureUsageEventLoggerProvider {

  override fun isEnabled() : Boolean {
    return false
  }

  override fun createLogger() : FeatureUsageEventLogger {
    return FeatureUsageEmptyEventLogger()
  }
}

class FeatureUsageEmptyEventLogger : FeatureUsageEventLogger {

  override fun log(recorderId: String, action: String, isState: Boolean) {
  }

  override fun log(recorderId: String, action: String, data: Map<String, Any>, isState: Boolean) {
  }

  override fun getLogFiles(): List<File> {
    return emptyList()
  }
}

fun getLoggerProvider(): FeatureUsageEventLoggerProvider {
  if (Extensions.getRootArea().hasExtensionPoint(EP_NAME.name)) {
    val extensions = EP_NAME.extensions
    if (extensions.isEmpty()) {
      LOG.warn("Cannot find feature usage event logger")
    }
    else if (extensions.size > 1) {
      LOG.warn("Too many feature usage loggers registered (" + Arrays.asList<FeatureUsageEventLoggerProvider>(*extensions) + ")")
    }
    return extensions[0]
  }
  return FeatureUsageEmptyEventLoggerProvider()
}