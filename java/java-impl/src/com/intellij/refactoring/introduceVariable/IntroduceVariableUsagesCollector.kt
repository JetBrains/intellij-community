// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class IntroduceVariableUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("introduce.variable.inplace", 3)

    @JvmField
    val changed = EventFields.Boolean("changed")
    @JvmField
    val varType = EventFields.Boolean("varType")
    @JvmField
    val finalState = EventFields.Boolean("final")

    @JvmField
    val settingsChanged = GROUP.registerVarargEvent("settingsChanged", changed)

    @JvmField
    val settingsOnPerform = GROUP.registerVarargEvent("settingsOnHide", varType, finalState)

    @JvmField
    val settingsOnShow = GROUP.registerVarargEvent("settingsOnShow", EventFields.InputEvent, varType, finalState)

  }
}