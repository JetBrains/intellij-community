// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.CommonBundle
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBInsets
import icons.CollaborationToolsIcons
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel

object CodeReviewCommentUIUtil {

  const val INLAY_PADDING = 10
  private const val EDITOR_INLAY_PANEL_ARC = 10

  fun getInlayPadding(componentType: CodeReviewChatItemUIUtil.ComponentType): Insets {
    val paddingInsets = componentType.paddingInsets
    val top = INLAY_PADDING - paddingInsets.top
    val bottom = INLAY_PADDING - paddingInsets.bottom
    return JBInsets(top, 0, bottom, 0)
  }

  fun createEditorInlayPanel(component: JComponent): JPanel {
    val roundedLineBorder = IdeBorderFactory.createRoundedBorder(EDITOR_INLAY_PANEL_ARC).apply {
      setColor(JBColor.lazy {
        val scheme = EditorColorsManager.getInstance().globalScheme
        scheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()
      })
    }
    return RoundedPanel(BorderLayout(), EDITOR_INLAY_PANEL_ARC - 2).apply {
      border = roundedLineBorder
      background = JBColor.lazy {
        val scheme = EditorColorsManager.getInstance().globalScheme
        scheme.defaultBackground
      }
      add(component)
    }.also {
      component.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) =
          it.dispatchEvent(ComponentEvent(component, ComponentEvent.COMPONENT_RESIZED))
      })
    }
  }

  fun createDeleteCommentIconButton(actionListener: (ActionEvent) -> Unit): JComponent {
    val icon = CollaborationToolsIcons.Delete
    val hoverIcon = CollaborationToolsIcons.DeleteHovered
    val button = InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.delete"))
    button.actionListener = ActionListener {
      if (MessageDialogBuilder.yesNo(CollaborationToolsBundle.message("review.comments.delete.confirmation.title"),
                                     CollaborationToolsBundle.message("review.comments.delete.confirmation")).ask(button)) {
        actionListener(it)
      }
    }
    return button
  }

  fun createEditButton(actionListener: (ActionEvent) -> Unit): InlineIconButton {
    val icon = AllIcons.General.Inline_edit
    val hoverIcon = AllIcons.General.Inline_edit_hovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.edit")).apply {
      this.actionListener = ActionListener {
        actionListener(it)
      }
    }
  }

  object Title {
    const val HORIZONTAL_GAP = 8
    const val GROUP_HORIZONTAL_GAP = 12
  }

  object Actions {
    const val HORIZONTAL_GAP = 8
  }
}