// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.services.ServiceViewEmptyTreeSuggestion
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButtonUtil
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import java.awt.event.InputEvent
import javax.swing.Icon

class AddServiceEmptyTreeSuggestion : ServiceViewEmptyTreeSuggestion {
  private val ADD_SERVICE_ACTION_ID: String = "ServiceView.AddService"

  // Always the last
  override val weight: Int = -10

  override val icon: Icon? = AllIcons.General.Add

  override val text: String = ExecutionBundle.message("service.view.empty.tree.suggestion.add.service")

  override val shortcutText: String?
    get() {
      val addAction = ActionManager.getInstance().getAction(ADD_SERVICE_ACTION_ID)
      val shortcutSet = addAction?.shortcutSet
      val shortcut = shortcutSet?.getShortcuts()?.firstOrNull() ?: return null

      return KeymapUtil.getShortcutText(shortcut)
    }

  override fun onActivate(dataContext: DataContext, inputEvent: InputEvent?) {
    val selectedView = ServiceViewActionProvider.getSelectedView(dataContext) ?: return
    val action = ActionManager.getInstance().getAction(ADD_SERVICE_ACTION_ID)
    val actionGroup = action as? ActionGroup ?: return

    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      "",
      actionGroup,
      dataContext,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false,
      ActionPlaces.getActionGroupPopupPlace(ADD_SERVICE_ACTION_ID))
    val button = ActionButtonUtil.findActionButtonById(selectedView, ADD_SERVICE_ACTION_ID) ?: return
    popup.addListener(object : JBPopupListener {
      override fun beforeShown(event: LightweightWindowEvent) {
        Toggleable.setSelected(button, true)
      }

      override fun onClosed(event: LightweightWindowEvent) {
        Toggleable.setSelected(button, null)
      }
    })
    popup.showUnderneathOf(button)
  }
}