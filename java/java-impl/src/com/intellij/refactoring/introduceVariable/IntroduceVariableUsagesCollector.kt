// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class IntroduceVariableUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("introduce.variable.inplace", 1)

    @JvmField
    val changed = EventFields.Boolean("changed")
    @JvmField
    val varType = EventFields.Boolean("varType")
    @JvmField
    val finalState = EventFields.Boolean("final")

    @JvmField
    val settingsChanged = register("settingsChanged")
    @JvmField
    val settingsOnPerform = register("settingsOnHide")
    @JvmField
    val settingsOnShow = register("settingsOnShow")

    private fun register(eventId: String): VarargEventId = GROUP.registerVarargEvent(eventId, EventFields.InputEvent, varType, finalState, changed)
  }
}