// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class RenameInplacePopupUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("rename.inplace.popup", 2)

    @JvmField val changedOnHide = EventFields.Boolean("changedOnHide")
    @JvmField val linkUsed = EventFields.Boolean("linkUsed")
    @JvmField val searchInCommentsOnHide = EventFields.Boolean("search_in_comments_on_hide")
    @JvmField val searchInTextOccurrencesOnHide = EventFields.Boolean("search_in_text_occurrences_on_hide")
    

    @JvmField val show = registerInplacePopupEventEvent("show");
    @JvmField val hide = registerInplacePopupEventEvent("hide");
    @JvmField val openRenameDialog = registerInplacePopupEventEvent("openRenameDialog");
    @JvmField val settingsChanged = registerInplacePopupEventEvent("settingsChanged");

    private fun registerInplacePopupEventEvent(eventId: String) =
      GROUP.registerVarargEvent(eventId, EventFields.InputEvent, searchInCommentsOnHide, searchInTextOccurrencesOnHide, changedOnHide, linkUsed)
  }
}