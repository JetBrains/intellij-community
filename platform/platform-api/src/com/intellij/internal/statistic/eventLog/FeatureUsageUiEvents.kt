// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable

fun getUiEventLogger(): FeatureUsageUiEvents {
  if (ApplicationManager.getApplication() != null) {
    return ApplicationManager.getApplication().getService(FeatureUsageUiEvents::class.java) ?: EmptyFeatureUsageUiEvents
  }
  // cannot load service if application is not initialized
  return EmptyFeatureUsageUiEvents
}

interface FeatureUsageUiEvents {
  fun logSelectConfigurable(configurable: Configurable)

  fun logApplyConfigurable(configurable: Configurable)

  fun logResetConfigurable(configurable: Configurable)

  fun logShowDialog(clazz: Class<*>)

  fun logCloseDialog(clazz: Class<*>, exitCode: Int)

  fun logClickOnHelpDialog(clazz: Class<*>)
}

object EmptyFeatureUsageUiEvents : FeatureUsageUiEvents {
  override fun logSelectConfigurable(configurable: Configurable) {
  }

  override fun logApplyConfigurable(configurable: Configurable) {
  }

  override fun logResetConfigurable(configurable: Configurable) {
  }

  override fun logShowDialog(clazz: Class<*>) {
  }

  override fun logCloseDialog(clazz: Class<*>, exitCode: Int) {
  }

  override fun logClickOnHelpDialog(clazz: Class<*>) {
  }
}