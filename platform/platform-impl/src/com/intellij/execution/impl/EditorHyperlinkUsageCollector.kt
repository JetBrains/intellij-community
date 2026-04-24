// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object EditorHyperlinkUsageCollector : CounterUsagesCollector() {

  private val GROUP: EventLogGroup = EventLogGroup("editor.hyperlink", 1)

  private val invisibleHyperlinkFollowedEvent = GROUP.registerEvent(
    "invisible.hyperlink.followed",
    EventFields.Enum<HyperlinkFollowedPlace>("hyperlink_followed_place"),
  )

  private val invisibleHyperlinkPopupShownEvent = GROUP.registerEvent(
    "invisible.hyperlink.popup.shown",
    EventFields.Boolean("editor_was_focused_before_popup_shown"),
  )

  private val invisibleHyperlinkPopupHiddenEvent = GROUP.registerEvent(
    "invisible.hyperlink.popup.hidden",
    EventFields.Boolean("editor_was_focused_before_popup_shown"),
    EventFields.Boolean("popup_closed_by_following_link"),
  )

  override fun getGroup(): EventLogGroup = GROUP
  
  fun logInvisibleHyperlinkFollowed(place: HyperlinkFollowedPlace) {
    invisibleHyperlinkFollowedEvent.log(place)
  }

  fun logInvisibleHyperlinkPopupShown(wasEditorFocusedBeforePopupShown: Boolean) {
    invisibleHyperlinkPopupShownEvent.log(wasEditorFocusedBeforePopupShown)
  }

  fun logInvisibleHyperlinkPopupHidden(wasEditorFocusedBeforePopupShown: Boolean, popupClosedByFollowingLink: Boolean) {
    invisibleHyperlinkPopupHiddenEvent.log(wasEditorFocusedBeforePopupShown, popupClosedByFollowingLink)
  }

  enum class HyperlinkFollowedPlace {
    EDITOR_LINK_CTRL_CLICKED,
    POPUP_LINK_CLICKED,
  }
}
