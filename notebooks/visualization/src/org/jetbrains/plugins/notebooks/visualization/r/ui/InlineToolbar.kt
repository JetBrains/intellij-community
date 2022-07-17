/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.psi.PsiElement
import java.awt.*
import javax.swing.JPanel


class InlineToolbar(val cell: PsiElement,
                    private val editor: Editor,
                    toolbarActions: ActionGroup) : JPanel(BorderLayout()) {

  private var numSoftWraps = 0
  private var couldUpdateBound = false
  private val actionToolBar = ActionManager.getInstance().createActionToolbar("InlineToolbar", toolbarActions, true).apply {
    setTargetComponent(editor.contentComponent)
  }

  init {
    setOpaque(false)
    setBackground(Color(0, 0, 0, 0))
    val component = actionToolBar.component
    component.isOpaque = false
    component.background = Color(0, 0, 0, 0)
    component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    add(component)
    addSoftWrapListener()
  }

  private fun addSoftWrapListener() {
    val softWrapModel = editor.softWrapModel as SoftWrapModelImpl
    softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() {
        if (!softWrapModel.isSoftWrappingEnabled && numSoftWraps > 0) {
          numSoftWraps = 0
          updateBounds()
        }
      }

      override fun recalculationEnds() {
        val newNumSoftWraps = softWrapModel.registeredSoftWraps.size
        if (softWrapModel.isSoftWrappingEnabled && numSoftWraps != newNumSoftWraps) {
          numSoftWraps = newNumSoftWraps
          updateBounds()
        }
      }
    })
    numSoftWraps = if (softWrapModel.isSoftWrappingEnabled) softWrapModel.registeredSoftWraps.size else 0
  }

  override fun paint(g: Graphics) {
    // We need this fix with AlphaComposite.SrcOver to resolve problem of black background on transparent images such as icons.
    //ToDo: - We need to make some tests on mac and linux for this, maybe this is applicable only to windows platform.
    //      - And also we need to check this on different Windows versions (definitely we have problems on Windows) .
    //      - Definitely we have problems on new macBook
    val oldComposite = (g as Graphics2D).composite
    g.composite = AlphaComposite.SrcOver
    super<JPanel>.paint(g)
    g.composite = oldComposite
  }

  fun updateBounds() {
    if (editor.isDisposed || !cell.isValid) return
    val toolbarWidth = actionToolBar.component.preferredSize.width
    val toolbarHeight = actionToolBar.component.preferredSize.height
    val gutterWidth = (editor.gutter as EditorGutterComponentEx).width
    val editorBounds = editor.component.bounds
    val deltaY = (toolbarHeight - editor.lineHeight) / 2
    val newToolbarX = editorBounds.x + editorBounds.width - toolbarWidth - gutterWidth
    val visual = editor.offsetToVisualPosition(cell.textRange.endOffset - 1)
    val newToolbarY = editor.visualLineToY(visual.line) - deltaY
    try {
      couldUpdateBound = true
      bounds = Rectangle(newToolbarX, newToolbarY, toolbarWidth, toolbarHeight)
    } finally {
      couldUpdateBound = false
    }
    invalidate()
    repaint()
  }
}