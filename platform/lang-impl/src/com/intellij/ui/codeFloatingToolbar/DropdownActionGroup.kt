// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.ui.popup.PopupFactoryImpl
import javax.swing.JComponent

class DropdownActionGroup: DefaultActionGroup(), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object: ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun actionPerformed(event: AnActionEvent) {
        val group = this@DropdownActionGroup
        val popupPlace = ActionPlaces.getPopupPlace(place)
        val context = event.dataContext
        val popup = PopupFactoryImpl.getInstance().createActionGroupPopup(null, group, context, null, true, popupPlace)
        popup.setRequestFocus(false)
        popup.showUnderneathOf(this)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val activeActions = ActionGroupUtil.getActiveActions(this, e)
    e.presentation.isEnabled = activeActions.isNotEmpty
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}