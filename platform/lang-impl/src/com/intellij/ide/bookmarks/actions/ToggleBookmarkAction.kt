// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.icons.AllIcons.Actions.Checked
import com.intellij.ide.bookmarks.BookmarkBundle.messagePointer
import com.intellij.ide.bookmarks.BookmarkType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction

internal class ToggleBookmarkAction : Toggleable, DumbAwareAction(messagePointer("bookmark.toggle.action.text")) {

  override fun update(event: AnActionEvent) {
    val context = event.dataContext.context
    val selected = context?.bookmark != null
    event.presentation.apply {
      isEnabledAndVisible = context != null
      icon = when (event.place) {
        ActionPlaces.TOUCHBAR_GENERAL -> {
          Toggleable.setSelected(this, selected)
          Checked
        }
        else -> null
      }
      setText(when {
                !ActionPlaces.isPopupPlace(event.place) -> messagePointer("bookmark.toggle.action.text")
                selected -> messagePointer("bookmark.delete.action.text")
                else -> messagePointer("bookmark.add.action.text")
              })
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val context = event.dataContext.context ?: return
    context.setType(context.bookmark?.type ?: BookmarkType.DEFAULT)
  }
}
