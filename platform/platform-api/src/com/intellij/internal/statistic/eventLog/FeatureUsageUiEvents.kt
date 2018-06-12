// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.containers.ContainerUtil

object FeatureUsageUiEvents {
  private const val DIALOGS_ID = "ui.dialogs"
  private const val SETTINGS_ID = "ui.settings"

  private val SELECT_CONFIGURABLE_DATA = HashMap<String, Any>()
  private val APPLY_CONFIGURABLE_DATA = HashMap<String, Any>()
  private val RESET_CONFIGURABLE_DATA = HashMap<String, Any>()

  private val SHOW_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()
  private val CLOSE_OK_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()
  private val CLOSE_CANCEL_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()
  private val CLOSE_CUSTOM_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()

  init {
    SELECT_CONFIGURABLE_DATA["type"] = "select"
    APPLY_CONFIGURABLE_DATA["type"] = "apply"
    RESET_CONFIGURABLE_DATA["type"] = "reset"

    SHOW_DIALOG_DATA["type"] = "show"
    CLOSE_OK_DIALOG_DATA["type"] = "close"
    CLOSE_OK_DIALOG_DATA["code"] = DialogWrapper.OK_EXIT_CODE
    CLOSE_CANCEL_DIALOG_DATA["type"] = "close"
    CLOSE_CANCEL_DIALOG_DATA["code"] = DialogWrapper.CANCEL_EXIT_CODE
    CLOSE_CUSTOM_DIALOG_DATA["type"] = "close"
    CLOSE_CUSTOM_DIALOG_DATA["code"] = DialogWrapper.NEXT_USER_EXIT_CODE
  }

  fun logSelectConfigurable(name: String) {
    FeatureUsageLogger.log(SETTINGS_ID, name, SELECT_CONFIGURABLE_DATA)
  }

  fun logApplyConfigurable(name: String) {
    FeatureUsageLogger.log(SETTINGS_ID, name, APPLY_CONFIGURABLE_DATA)
  }

  fun logResetConfigurable(name: String) {
    FeatureUsageLogger.log(SETTINGS_ID, name, RESET_CONFIGURABLE_DATA)
  }

  fun logShowDialog(name: String) {
    FeatureUsageLogger.log(DIALOGS_ID, name, SHOW_DIALOG_DATA)
  }

  fun logCloseDialog(name: String, exitCode: Int) {
    if (FeatureUsageLogger.isEnabled()) {
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        FeatureUsageLogger.log(DIALOGS_ID, name, CLOSE_OK_DIALOG_DATA)
      }
      else if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
        FeatureUsageLogger.log(DIALOGS_ID, name, CLOSE_CANCEL_DIALOG_DATA)
      }
      else {
        FeatureUsageLogger.log(DIALOGS_ID, name, CLOSE_CUSTOM_DIALOG_DATA)
      }
    }
  }
}
