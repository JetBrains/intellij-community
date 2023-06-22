// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.pycharmUi.state

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ConfigImportHelper
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NewUserFirstRunDateTracker : AppLifecycleListener {
  override fun appStarted() {
    val state = PyCharmUIInfoService.getInstance().state
    if (ConfigImportHelper.isNewUser() && state.newUserFirstRunDate == null) {
      state.newUserFirstRunDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
  }
}