// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.util.ui.JBUI
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke

private const val NAVIGATION_PLACE = "DefinitionChooser"

class DefinitionSwitcher<T>(var elements: Array<T>,
                            private val component: JComponent,
                            private val onUpdate: (T)-> Unit) {
  var index = 0
  fun getCurrentElement() = elements[index]

  fun createToolbar(additionalAction: AnAction? = null): ActionToolbar {
    val group = DefaultActionGroup()
    val back = navigationAction(CodeInsightBundle.messagePointer("quick.definition.back"), AllIcons.Actions.Play_back, -1).apply {
      registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), component)
    }
    group.add(back)

    group.add(object : ToolbarLabelAction() {
      override fun createCustomComponent(presentation: Presentation,
                                         place: String): JComponent {
        val component = super.createCustomComponent(presentation, place)
        component.border = JBUI.Borders.empty(0, 2)
        return component
      }

      override fun update(e: AnActionEvent) {
        super.update(e)
        val presentation = e.presentation
        if (elements.isNotEmpty()) {
          presentation.text = (index + 1).toString() + "/" + elements.size
          presentation.isVisible = true
        }
        else {
          presentation.isVisible = false
        }
      }

      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
      }
    })

    val forward = navigationAction(CodeInsightBundle.messagePointer("quick.definition.forward"),
                                   AllIcons.Actions.Play_forward, 1).apply {
      registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), component)
    }
    group.add(forward)

    if (additionalAction != null) {
      group.add(additionalAction)
    }

    return ActionManager.getInstance().createActionToolbar(NAVIGATION_PLACE, group, true).apply {
      setReservePlaceAutoPopupIcon(false)
      targetComponent = component
    }
  }

  private fun navigationAction(name: Supplier<String>, icon: Icon, direction: Int): AnAction {
    return object: AnAction(name, icon), ActionToIgnore {
      override fun actionPerformed(e: AnActionEvent) {
        val i = index + direction
        index = when {
          i >= elements.size -> 0
          i < 0 -> elements.size - 1
          else -> i
        }
        onUpdate(getCurrentElement())
      }

      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
      }
    }
  }
}