// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

object FeatureUsageUiEvents {
  private const val UI_RECORDER_ID = "ui-recorder"

  private val SELECT_CONFIGURABLE_DATA = HashMap<String, Any>()
  private val APPLY_CONFIGURABLE_DATA = HashMap<String, Any>()
  private val RESET_CONFIGURABLE_DATA = HashMap<String, Any>()

  init {
    SELECT_CONFIGURABLE_DATA["type"] = "select"
    APPLY_CONFIGURABLE_DATA["type"] = "apply"
    RESET_CONFIGURABLE_DATA["type"] = "reset"
  }

  fun logSelectConfigurable(name: String) {
    FeatureUsageLogger.log(UI_RECORDER_ID, name, SELECT_CONFIGURABLE_DATA)
  }

  fun logApplyConfigurable(name: String) {
    FeatureUsageLogger.log(UI_RECORDER_ID, name, APPLY_CONFIGURABLE_DATA)
  }

  fun logResetConfigurable(name: String) {
    FeatureUsageLogger.log(UI_RECORDER_ID, name, RESET_CONFIGURABLE_DATA)
  }
}
