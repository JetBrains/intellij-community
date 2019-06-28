// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable

fun getUiEventLogger(): FeatureUsageUiEvents {
  if (ApplicationManager.getApplication() != null) {
    return ServiceManager.getService(FeatureUsageUiEvents::class.java) ?: EmptyFeatureUsageUiEvents
  }
  // cannot load service if application is not initialized
  return EmptyFeatureUsageUiEvents
}

interface FeatureUsageUiEvents {
  fun logSelectConfigurable(configurable: Configurable)

  fun logApplyConfigurable(configurable: Configurable)

  fun logResetConfigurable(configurable: Configurable)

  fun logShowDialog(name: String, context: Class<*>)

  fun logCloseDialog(name: String, exitCode: Int, context: Class<*>)
}

object EmptyFeatureUsageUiEvents : FeatureUsageUiEvents {
  override fun logSelectConfigurable(configurable: Configurable) {
  }

  override fun logApplyConfigurable(configurable: Configurable) {
  }

  override fun logResetConfigurable(configurable: Configurable) {
  }

  override fun logShowDialog(name: String, context: Class<*>) {
  }

  override fun logCloseDialog(name: String, exitCode: Int, context: Class<*>) {
  }
}