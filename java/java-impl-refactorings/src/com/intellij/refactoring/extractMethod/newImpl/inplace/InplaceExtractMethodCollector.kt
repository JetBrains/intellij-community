// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class InplaceExtractMethodCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("extract.method.inplace", 2)


    @JvmField val settingsChange = EventFields.Enum("settingsChange", ExtractMethodSettingChange::class.java) { it.fusName }
    @JvmField val changedOnHide = EventFields.Boolean("changedOnHide")
    @JvmField val nameChanged = EventFields.Boolean("nameChanged")
    @JvmField val linkUsed = EventFields.Boolean("linkUsed")

    @JvmField val show = GROUP.registerEvent("showPopup", EventFields.InputEvent)
    @JvmField val hide = GROUP.registerEvent("hidePopup", changedOnHide)
    @JvmField val openExtractDialog = GROUP.registerEvent("openExtractDialog", linkUsed)
    @JvmField val executed = GROUP.registerEvent("executed", nameChanged)
    @JvmField val settingsChanged = GROUP.registerEvent("settingsChanged", settingsChange)
  }
}

enum class ExtractMethodSettingChange(val fusName: String) {
  AnnotateOn("AnnotateOn"), AnnotateOff("AnnotateOff"), MakeStaticOn("MakeStaticOn"),
  MakeStaticOff("MakeStaticOff"), MakeStaticWithFieldsOn("MakeStaticWithFieldsOn"),
  MakeStaticWithFieldsOff("MakeStaticWithFieldsOff")
}