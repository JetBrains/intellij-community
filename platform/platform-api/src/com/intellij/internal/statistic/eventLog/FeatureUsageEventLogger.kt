// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import java.io.File

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.FeatureUsageEventLogger")
private val EP_NAME = ExtensionPointName.create<FeatureUsageEventLoggerProvider>("com.intellij.statistic.eventLog.fusEventLoggerProvider")

interface FeatureUsageEventLogger {
  fun log(group: FeatureUsageGroup, action: String, isState: Boolean)
  fun log(group: FeatureUsageGroup, action: String, data: Map<String, Any>, isState: Boolean)
  fun getLogFiles(): List<File>
  fun cleanup()
}

interface FeatureUsageEventLoggerProvider {
  fun isEnabled() : Boolean
  fun createLogger() : FeatureUsageEventLogger
}

class FeatureUsageEmptyEventLoggerProvider : FeatureUsageEventLoggerProvider {
  override fun isEnabled() : Boolean = false
  override fun createLogger() : FeatureUsageEventLogger = FeatureUsageEmptyEventLogger()
}

class FeatureUsageEmptyEventLogger : FeatureUsageEventLogger {
  override fun log(group: FeatureUsageGroup, action: String, isState: Boolean) = Unit
  override fun log(group: FeatureUsageGroup, action: String, data: Map<String, Any>, isState: Boolean) = Unit
  override fun getLogFiles(): List<File> = emptyList()
  override fun cleanup() = Unit
}

fun getLoggerProvider(): FeatureUsageEventLoggerProvider {
  if (Extensions.getRootArea().hasExtensionPoint(EP_NAME.name)) {
    val extensions = EP_NAME.extensionList
    if (extensions.isEmpty()) {
      LOG.warn("Cannot find feature usage event logger")
      return FeatureUsageEmptyEventLoggerProvider()
    }
    else if (extensions.size > 1) {
      LOG.warn("Too many feature usage loggers registered (${extensions})")
    }
    return extensions[0]
  }
  return FeatureUsageEmptyEventLoggerProvider()
}

/**
 * Best practices:
 * - Prefer a bigger group with many (related) event types to many small groups of 1-2 events each.
 * - Prefer shorter group names; avoid common prefixes (such as "statistics.").
 */
class FeatureUsageGroup(val id: String, val version: Int)