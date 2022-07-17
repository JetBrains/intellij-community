// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel

object ReviewUIUtil {

  private const val EDITOR_INLAY_PANEL_ARC = 10

  fun createEditorInlayPanel(component: JComponent): JPanel {
    val roundedLineBorder = IdeBorderFactory.createRoundedBorder(EDITOR_INLAY_PANEL_ARC)
    return RoundedPanel(BorderLayout(), EDITOR_INLAY_PANEL_ARC - 2).apply {
      border = roundedLineBorder
      add(component)
    }.also {
      CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
        val scheme = EditorColorsManager.getInstance().globalScheme
        background = scheme.defaultBackground
        roundedLineBorder.setColor(scheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border())
      }
      component.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) =
          it.dispatchEvent(ComponentEvent(component, ComponentEvent.COMPONENT_RESIZED))
      })
    }
  }
}