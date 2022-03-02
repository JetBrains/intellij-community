// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class IntroduceParameterUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("introduce.parameter.inplace", 2)

    @JvmField
    val delegate = EventFields.Boolean("delegate")

    @JvmField
    val replaceAll = EventFields.Boolean("replaceAllOccurrences")

    @JvmField
    val settingsOnPerform = GROUP.registerVarargEvent("settingsOnHide", delegate)

    @JvmField
    val started = GROUP.registerVarargEvent("started", replaceAll)
  }
}