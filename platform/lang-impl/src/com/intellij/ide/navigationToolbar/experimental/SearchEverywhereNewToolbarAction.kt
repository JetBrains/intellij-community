// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.SearchEverywhereAction
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent

class SearchEverywhereNewToolbarAction : SearchEverywhereAction() {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : JButton() {
      init {
        icon = AllIcons.Actions.Search
        text = ActionsBundle.message("action.SearchEverywhere.text")
        getModel().isEnabled = false
        isVisible = presentation.isVisible
        isFocusable = ScreenReader.isActive()

        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent?) {
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
              if (e != null) {
                val e1 = MouseEvent(e.component, e.id, e.`when`, 0, 0, 0, 0, true, 0)
                actionPerformed(
                  AnActionEvent(e1, it, "", templatePresentation,
                                ActionManager.getInstance(), 0))
              }
            }
          }
        })
      }

      override fun getPreferredSize(): Dimension? {
        val prefSize = super.getPreferredSize()
        val i = insets
        val width = prefSize.width + (if (StringUtil.isNotEmpty(text)) iconTextGap else 0)
        var height = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height + i.top + i.bottom

        val size = Dimension(width, height)
        JBInsets.addTo(size, margin)
        return size
      }
    }
  }
}