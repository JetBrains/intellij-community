// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PopupInMainMenuActionGroup : NonTrivialActionGroup() {
  override fun update(e: AnActionEvent) {
    if (e.place == ActionPlaces.MAIN_MENU) {
      super.update(e)
      e.presentation.isPopupGroup = true
      return
    }

    if (!e.isFromContextMenu) {
      super.update(e)
      e.presentation.isPopupGroup = false
      return
    }

    val presentation = e.presentation
    val hasVisibleChildren = ActionGroupUtil.getVisibleActions(this, e).iterator().hasNext()
    val hasEnabledChildren = ActionGroupUtil.getActiveActions(this, e).iterator().hasNext()
    presentation.isVisible = hasVisibleChildren
    presentation.isEnabled = hasEnabledChildren
    presentation.isPopupGroup = false
  }
}
