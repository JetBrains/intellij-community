// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import com.intellij.openapi.observable.util.whenMouseMoved
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JComponent

internal class StructureViewFloatingToolbar(
  private val ownerComponent: JComponent,
  private val parentDisposable: Disposable,
) : AbstractFloatingToolbarComponent(
  DefaultActionGroup(MergeableActions((ActionManager.getInstance().getAction(ActionPlaces.STRUCTURE_VIEW_FLOATING_TOOLBAR) as ActionGroup))),
  parentDisposable
) {

  override val autoHideable: Boolean = false

  private var boundsWithoutScrolling: Rectangle? = null

  override fun isComponentOnHold(): Boolean {
    return isComponentUnderMouse() || isFocusAncestor()
  }

  override fun installMouseMotionWatcher() {
    ownerComponent.whenMouseMoved(parentDisposable) {
      scheduleShow()
    }
  }

  fun repaintOnYWithDy(y: Int, scrollingDy: Int) {
    hideImmediately()
    boundsWithoutScrolling = bounds
    boundsWithoutScrolling = Rectangle(0, y, minimumButtonSize.width, minimumButtonSize.height)
    if (scrollingDy <= 0)
      bounds = boundsWithoutScrolling!!
    else
      setBounds(0, y - scrollingDy, minimumButtonSize.width, minimumButtonSize.height)
    scheduleShow()
  }

  fun setScrollingDy(scrollingDy: Int) {
    val bounds = boundsWithoutScrolling ?: bounds
    setBounds(bounds.x, bounds.y - scrollingDy, bounds.width, bounds.height)
  }

  init {
    init(ownerComponent)
    val oneDimension = UIUtil.getTreeFont().size + UIUtil.getTreeRightChildIndent()
    minimumButtonSize = Dimension(oneDimension, oneDimension)
    showingTime = 150
    hidingTime = 50
    backgroundAlpha = 1F
    border = BorderFactory.createEmptyBorder()
    layoutStrategy = MyToolbarLayoutStrategy()
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