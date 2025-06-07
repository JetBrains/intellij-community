// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object InplaceExtractMethodCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("extract.method.inplace", 3)

  @JvmField val settingsChange = EventFields.Enum<ExtractMethodSettingChange>("settingsChange") { it.fusName }
  @JvmField val changedOnHide = EventFields.Boolean("changedOnHide")
  @JvmField val nameChanged = EventFields.Boolean("nameChanged")
  @JvmField val linkUsed = EventFields.Boolean("linkUsed")
  @JvmField val prepareTargetPlacesMs = EventFields.Long("prepare_target_places_ms")
  @JvmField val prepareTemplateMs = EventFields.Long("prepare_template_ms")
  @JvmField val prepareTotalMs = EventFields.Long("prepare_total_ms")
  @JvmField val numberOfTargetPlaces = EventFields.Int("number_of_target_places")

  @JvmField val show = GROUP.registerEvent("showPopup", EventFields.InputEvent)
  @JvmField val duplicatesSearched = GROUP.registerEvent("duplicates_searched", EventFields.DurationMs)
  @JvmField val previewUpdated = GROUP.registerEvent("preview_updated", EventFields.DurationMs)
  @JvmField val templateShown = GROUP.registerVarargEvent("template_shown", prepareTargetPlacesMs,
                                                          numberOfTargetPlaces, prepareTemplateMs, prepareTotalMs)
  @JvmField val hide = GROUP.registerEvent("hidePopup", changedOnHide)
  @JvmField val openExtractDialog = GROUP.registerEvent("openExtractDialog", linkUsed)
  @JvmField val executed = GROUP.registerEvent("executed", nameChanged)
  @JvmField val settingsChanged = GROUP.registerEvent("settingsChanged", settingsChange)
}

enum class ExtractMethodSettingChange(val fusName: String) {
  AnnotateOn("AnnotateOn"), AnnotateOff("AnnotateOff"), MakeStaticOn("MakeStaticOn"),
  MakeStaticOff("MakeStaticOff"), MakeStaticWithFieldsOn("MakeStaticWithFieldsOn"),
  MakeStaticWithFieldsOff("MakeStaticWithFieldsOff")
}