// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JComponent

internal class StructureViewFloatingToolbar(
  ownerComponent: JComponent,
  parentDisposable: Disposable,
) : AbstractFloatingToolbarComponent(
  DefaultActionGroup(MergeableActions((ActionManager.getInstance().getAction(ActionPlaces.STRUCTURE_VIEW_FLOATING_TOOLBAR) as ActionGroup))),
  ownerComponent,
  parentDisposable
) {

  private var boundsWithoutScrolling: Rectangle? = null

  fun repaintOnYWithDy(y: Int, scrollingDy: Int) {
    boundsWithoutScrolling = Rectangle(0, y, minimumButtonSize.width, minimumButtonSize.height)
    val newBounds = if (scrollingDy <= 0)
      boundsWithoutScrolling!!
    else
      Rectangle(0, y - scrollingDy, minimumButtonSize.width, minimumButtonSize.height)
    if (newBounds != bounds) {
      hideImmediately()
      bounds = newBounds
      scheduleShow()
      actionsUpdated(true, listOf(actionGroup))
    }
  }

  fun setScrollingDy(scrollingDy: Int) {
    val bounds = boundsWithoutScrolling ?: bounds
    setBounds(bounds.x, bounds.y - scrollingDy, bounds.width, bounds.height)
  }

  init {
    val oneDimension = UIUtil.getTreeFont().size + UIUtil.getTreeRightChildIndent()
    minimumButtonSize = Dimension(oneDimension, oneDimension)
    showingTime = 150
    hidingTime = 50
    backgroundAlpha = 0F
    border = BorderFactory.createEmptyBorder()
    layoutStrategy = MyToolbarLayoutStrategy()
  }

  init {
    scheduleShow()
  }

  private class MyToolbarLayoutStrategy: ToolbarLayoutStrategy {
    override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
      val toolbarComponent = toolbar.component
      if (toolbarComponent.componentCount == 0) return listOf()
      val icon = (toolbarComponent.components[0] as? ActionButton)?.icon ?: return listOf(Rectangle(0, 0))
      val indent = UIUtil.getTreeLeftChildIndent()
      return listOf(Rectangle(0, 0, icon.iconWidth + indent, icon.iconHeight + indent))
    }

    override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
      val toolbarComponent = toolbar.component
      if (toolbarComponent.componentCount == 0) return JBUI.emptySize()
      return toolbarComponent.components[0].preferredSize
    }

    override fun calcMinimumSize(toolbar: ActionToolbar): Dimension {
      return JBUI.emptySize()
    }
  }
}