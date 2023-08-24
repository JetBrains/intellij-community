// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields

object SwitcherLogger {
  val GROUP = EventLogGroup("recent.files.dialog", 1)

  val STATE = EventFields.Boolean("navigated")
  val NAVIGATED_INDEX = EventFields.Int("navigation.index")

  val SHOWN_TIME_ACTIVITY = GROUP.registerIdeActivity("shown_time", finishEventAdditionalFields = arrayOf(STATE, NAVIGATED_INDEX))
}