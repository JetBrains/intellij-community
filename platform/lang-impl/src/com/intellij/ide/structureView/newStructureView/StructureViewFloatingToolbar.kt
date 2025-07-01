// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JComponent

internal class StructureViewFloatingToolbar(
  ownerComponent: JComponent,
  parentDisposable: Disposable,
) : AbstractFloatingToolbarComponent(
  ActionManager.getInstance().getAction(ActionPlaces.STRUCTURE_VIEW_FLOATING_TOOLBAR) as ActionGroup,
  ownerComponent,
  parentDisposable
) {

  private var boundsWithoutScrolling: Rectangle? = null
  private var size: Int = 1

  fun repaintOnYWithDy(y: Int, scrollingDy: Int, size: Int) {
    this.size = size
    boundsWithoutScrolling = Rectangle(0, y, minimumButtonSize.width * size + 5, minimumButtonSize.height)
    val newBounds = if (scrollingDy <= 0)
      boundsWithoutScrolling!!
    else
      Rectangle(0, y - scrollingDy, minimumButtonSize.width * size + 5, minimumButtonSize.height)
    if (newBounds != bounds) {
      hideImmediately()
      bounds = newBounds
      scheduleShow()
      actionsUpdated(true, listOf(ActionManager.getInstance().getAction(ActionPlaces.STRUCTURE_VIEW_FLOATING_TOOLBAR)))
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
    layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
    setActionButtonBorder(0, 0)
    scheduleShow()
  }
}