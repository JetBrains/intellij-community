// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.ui.ideScaleIndicator

import com.intellij.ide.actions.ResetIdeScaleAction
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.percentValue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.ui.UIBundle
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGapsX
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.hover.HoverListener
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JPanel

class IdeScaleIndicator(percentage: Int) : JPanel() {
  private val titleLabel = JBLabel(UIBundle.message("ide.scale.indicator.current.scale.format", percentage))
  private val resetLink = ActionManager.getInstance().getAction(ResetIdeScaleAction::class.java.simpleName).run {
    AnActionLink(this, ActionPlaces.POPUP).also {
      it.text = UIBundle.message("ide.scale.indicator.reset.scale.format",
                                 UISettingsUtils.getInstance().currentDefaultScale.percentValue)
    }
  }
  var isHovered: Boolean = false
    private set

  init {
    border = JBUI.Borders.empty(6)
    layout = GridLayout()
    RowsGridBuilder(this).row(resizable = true)
      .cell(component = titleLabel, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)
      .cell(component = resetLink, verticalAlign = VerticalAlign.CENTER, resizableColumn = true)
      .columnsGaps(listOf(UnscaledGapsX.EMPTY, UnscaledGapsX(18)))

    if (percentage == UISettingsUtils.getInstance().currentDefaultScale.percentValue) resetLink.isVisible = false

    val hoverListener: HoverListener = object : HoverListener() {
      override fun mouseEntered(component: Component, x: Int, y: Int) {
        isHovered = true
      }

      override fun mouseMoved(component: Component, x: Int, y: Int) {
        isHovered = true
      }

      override fun mouseExited(component: Component) {
        isHovered = false
      }
    }
    hoverListener.addTo(this)
  }
}