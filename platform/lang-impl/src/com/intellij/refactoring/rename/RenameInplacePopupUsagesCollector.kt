// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object RenameInplacePopupUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("rename.inplace.popup", 3)

  @JvmField
  val changedOnHide: BooleanEventField = EventFields.Boolean("changedOnHide")

  @JvmField
  val linkUsed: BooleanEventField = EventFields.Boolean("linkUsed")

  @JvmField
  val searchInCommentsOnHide: BooleanEventField = EventFields.Boolean("search_in_comments_on_hide")

  @JvmField
  val searchInTextOccurrencesOnHide: BooleanEventField = EventFields.Boolean("search_in_text_occurrences_on_hide")

  @JvmField
  val show: VarargEventId = GROUP.registerVarargEvent("show", EventFields.InputEvent)

  @JvmField
  val hide: VarargEventId = GROUP.registerVarargEvent("hide", searchInCommentsOnHide, searchInTextOccurrencesOnHide)

  @JvmField
  val openRenameDialog: VarargEventId = GROUP.registerVarargEvent("openRenameDialog", linkUsed)

  @JvmField
  val settingsChanged: VarargEventId = GROUP.registerVarargEvent("settingsChanged", changedOnHide)
}
