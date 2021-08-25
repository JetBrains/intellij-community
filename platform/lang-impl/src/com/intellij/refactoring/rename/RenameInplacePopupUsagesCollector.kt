// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class RenameInplacePopupUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("rename.inplace.popup", 3)

    @JvmField val changedOnHide = EventFields.Boolean("changedOnHide")
    @JvmField val linkUsed = EventFields.Boolean("linkUsed")
    @JvmField val searchInCommentsOnHide = EventFields.Boolean("search_in_comments_on_hide")
    @JvmField val searchInTextOccurrencesOnHide = EventFields.Boolean("search_in_text_occurrences_on_hide")
    

    @JvmField val show = GROUP.registerVarargEvent("show", EventFields.InputEvent)
    @JvmField val hide = GROUP.registerVarargEvent("hide", searchInCommentsOnHide, searchInTextOccurrencesOnHide)
    @JvmField val openRenameDialog = GROUP.registerVarargEvent("openRenameDialog", linkUsed)
    @JvmField val settingsChanged = GROUP.registerVarargEvent("settingsChanged", changedOnHide)
  }
}