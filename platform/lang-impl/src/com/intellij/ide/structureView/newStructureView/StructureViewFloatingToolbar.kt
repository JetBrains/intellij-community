// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import com.intellij.openapi.observable.util.whenMouseMoved
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
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
    showingTime = 150
    hidingTime = 50
    backgroundAlpha = 1F
    border = BorderFactory.createEmptyBorder()
  }
}