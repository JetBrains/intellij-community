// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.diagnostic.Logger

import com.intellij.openapi.extensions.ExtensionPointName
import java.io.File
import java.util.*

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.FeatureUsageEventLogger")
private val EP_NAME = ExtensionPointName.create<FeatureUsageEventLogger>("com.intellij.statistic.eventLog.featureUsageEventLogger")

interface FeatureUsageEventLogger {

  fun log(recorderId: String, action: String)

  fun log(recorderId: String, action: String, data: Map<String, Any>)

  fun getLogFiles(): List<File>

}

class FeatureUsageEmptyEventLogger : FeatureUsageEventLogger {

  override fun log(recorderId: String, action: String) {
  }

  override fun log(recorderId: String, action: String, data: Map<String, Any>) {
  }

  override fun getLogFiles(): List<File> {
    return emptyList()
  }
}

fun getLogger(): FeatureUsageEventLogger {
  val extensions = EP_NAME.extensions
  if (extensions.isEmpty()) {
    LOG.warn("Cannot find feature usage event logger (" + Arrays.asList<FeatureUsageEventLogger>(*extensions) + ")")
    return FeatureUsageEmptyEventLogger()
  }
  else if (extensions.size > 1) {
    LOG.warn("Too many feature usage loggers registered (" + Arrays.asList<FeatureUsageEventLogger>(*extensions) + ")")
  }
  return extensions[0]
}