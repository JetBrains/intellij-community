// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class RenameUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("rename.refactoring", 4)
    private val GROUP_INPLACE = EventLogGroup("rename.inplace.popup", 0)

    @JvmField val scopeType = EventFields.Enum("scope_type", RenameScopeType::class.java) { it.fusName }
    @JvmField val searchInComments = EventFields.Boolean("search_in_comments")
    @JvmField val searchInTextOccurrences = EventFields.Boolean("search_in_text_occurrences")
    @JvmField val renameProcessor = EventFields.Class("rename_processor")

    @JvmField val started = registerRenameProcessorEvent("started")
    @JvmField val executed = registerRenameProcessorEvent("executed")
    
    private val referenceClass = EventFields.Class("reference_class")
    @JvmField val referenceProcessed = GROUP.registerEvent("reference.processed", referenceClass)

    private fun registerRenameProcessorEvent(eventId: String) =
      GROUP.registerVarargEvent(eventId, scopeType, searchInComments, searchInTextOccurrences, renameProcessor, EventFields.Language)

    @JvmField val changedOnHide = EventFields.Boolean("changedOnHide")
    @JvmField val linkUsed = EventFields.Boolean("linkUsed")

    @JvmField val show = registerInplacePopupEventEvent("show");
    @JvmField val hide = registerInplacePopupEventEvent("hide");
    @JvmField val openRenameDialog = registerInplacePopupEventEvent("openRenameDialog");
    @JvmField val settingsChanged = registerInplacePopupEventEvent("settingsChanged");

    private fun registerInplacePopupEventEvent(eventId: String) =
      GROUP_INPLACE.registerVarargEvent(eventId, EventFields.InputEvent, searchInComments, searchInTextOccurrences, changedOnHide, linkUsed)
  }
}

enum class RenameScopeType(val fusName: String) {
  Project("project"), Tests("tests"), Production("production"), CurrentFile("current_file"), Module("module"),
  ThirdParty("third.party"), Unknown("unknown")

}