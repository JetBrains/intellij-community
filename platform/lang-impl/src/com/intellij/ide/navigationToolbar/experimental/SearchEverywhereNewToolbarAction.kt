// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoClassPresentationUpdater.getActionTitlePluralized
import com.intellij.ide.actions.SearchEverywhereAction
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.BigPopup.searchFieldBackground
import com.intellij.util.ui.JBUI.CurrentTheme.TabbedPane.DISABLED_TEXT_COLOR
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JTextField

class SearchEverywhereNewToolbarAction : SearchEverywhereAction() {

  init {
    templatePresentation.icon = AllIcons.Actions.Search
    templatePresentation.text = ActionsBundle.message("action.SearchEverywhere.text")
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {

    return object : ActionButtonWithText(this, presentation, place,
                                         Dimension(JBUI.scale(130), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)) {
      init {
        presentation.icon = AllIcons.Actions.Search
        presentation.text = ActionsBundle.message("action.SearchEverywhere.text")
      }

      override fun updateToolTipText() {
        val shortcutText = getShortcut()
        val classesTabName = java.lang.String.join("/", getActionTitlePluralized())
        if (Registry.`is`("ide.helptooltip.enabled")) {
          HelpTooltip.dispose(this)
          HelpTooltip()
            .setTitle(myPresentation.text)
            .setShortcut(shortcutText)
            .setDescription(IdeBundle.message("search.everywhere.action.tooltip.description.text", classesTabName))
            .installOn(this)
        }
        else {
          toolTipText = IdeBundle.message("search.everywhere.action.tooltip.text", shortcutText, classesTabName)
        }
      }

      override fun actionPerformed(e: AnActionEvent?) {
        if (e != null) {
          super.actionPerformed(
            AnActionEvent(e.inputEvent, e.dataContext, ActionPlaces.NEW_TOOLBAR, templatePresentation,
                          ActionManager.getInstance(), 0))
        }
      }

      override fun paint(g: Graphics?) {
        foreground = DISABLED_TEXT_COLOR
        background = searchFieldBackground()
        super.paint(g)
      }
    }
  }
}