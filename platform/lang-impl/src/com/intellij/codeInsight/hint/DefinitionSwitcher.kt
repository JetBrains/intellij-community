// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.ui.components.JBLabel
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingConstants

private const val NAVIGATION_PLACE = "DefinitionChooser"

@ApiStatus.Internal
class DefinitionSwitcher<T>(elements: Array<T>,
                            private val component: JComponent,
                            private val onUpdate: (T)-> Unit) {
  var elements: Array<T> = elements
    set(value) {
      field = value
      maxLabelSize = getMaxLabelSize()
    }

  private var maxLabelSize = getMaxLabelSize()
  private fun jbEmptyBorder() = JBUI.Borders.empty(0, 2)

  private fun getMaxLabelSize(): Dimension {
    val label = JBLabel().withFont(JBUI.Fonts.toolbarFont()).withBorder(jbEmptyBorder())
    val maxWidth = (1..elements.size).maxOf {
      label.text = "${it}/${elements.size}"
      label.preferredWidth
    }
    return Dimension(maxWidth, label.preferredHeight)
  }

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
                                         place: String): JComponent =
        (super.createCustomComponent(presentation, place) as JBLabel).apply {
          border = jbEmptyBorder()
          horizontalAlignment = SwingConstants.TRAILING
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

      override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        super.updateCustomComponent(component, presentation)
        component.preferredSize = maxLabelSize
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