// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun getUiEventLogger(): FeatureUsageUiEvents {
  if (ApplicationManager.getApplication() != null) {
    return ApplicationManager.getApplication().getService(FeatureUsageUiEvents::class.java) ?: EmptyFeatureUsageUiEvents
  }
  // cannot load service if application is not initialized
  return EmptyFeatureUsageUiEvents
}

@ApiStatus.Internal
interface FeatureUsageUiEvents {
  fun logSelectConfigurable(configurable: Configurable, loadedFromCache: Boolean, loadTimeMs: Long)

  fun logApplyConfigurable(configurable: Configurable)

  fun logResetConfigurable(configurable: Configurable)

  fun logShowDialog(dialogClass: Class<*>)

  fun logCloseDialog(dialogClass: Class<*>, exitCode: Int)

  fun logClickOnHelpDialog(dialogClass: Class<*>)
}

@ApiStatus.Internal
object EmptyFeatureUsageUiEvents : FeatureUsageUiEvents {
  override fun logSelectConfigurable(configurable: Configurable, loadedFromCache: Boolean, loadTimeMs: Long) {
  }

  override fun logApplyConfigurable(configurable: Configurable) {
  }

  override fun logResetConfigurable(configurable: Configurable) {
  }

  override fun logShowDialog(dialogClass: Class<*>) {
  }

  override fun logCloseDialog(dialogClass: Class<*>, exitCode: Int) {
  }

  override fun logClickOnHelpDialog(dialogClass: Class<*>) {
  }
}