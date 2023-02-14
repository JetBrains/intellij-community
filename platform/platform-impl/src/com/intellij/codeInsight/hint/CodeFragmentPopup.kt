// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

internal class CodeFragmentPopup(val editor: Editor, val lines: IntRange, private val onClickAction: () -> Unit): Disposable {

  private val fillConstraints = GridBagConstraints().apply {
    fill = GridBagConstraints.BOTH
    weightx = 1.0
    weighty = 1.0
    gridx = 0
    gridy = 0
  }

  private val content = JPanel(GridBagLayout()).apply {
    add(createEditorFragment(editor, lines), fillConstraints)
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        onClickAction()
      }
    })
  }

  private val popup = createFragmentPopup(content)

  private fun createFragmentPopup(content: JPanel): JBPopup {
    return JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
      .setNormalWindowLevel(true)
      .setCancelOnClickOutside(false)
      .setRequestFocus(false)
      .setFocusable(false)
      .setMovable(false)
      .setResizable(false)
      .setShowBorder(false)
      .setCancelKeyEnabled(false)
      .setCancelOnWindowDeactivation(false)
      .setCancelOnOtherWindowOpen(false)
      .createPopup()
  }

  private fun createEditorFragment(editor: Editor, lines: IntRange): EditorFragmentComponent {
    return EditorFragmentComponent.createEditorFragmentComponent(editor, lines.first, lines.last + 1, true, true)
  }

  fun updateCodePreview() {
    if (lines.last > editor.document.lineCount) return
    val editorFragmentComponent = createEditorFragment(editor, lines)
    content.removeAll()
    content.add(editorFragmentComponent, fillConstraints)
  }

  fun show() {
    if (popup.canShow()){
      popup.show(RelativePoint(editor.component, Point(0, 0)))
    }
    else {
      popup.setUiVisible(true)
    }
    popup.setMinimumSize(Dimension(editor.component.width, 0))
  }

  fun hide() {
    popup.setUiVisible(false)
  }

  override fun dispose() {
    popup.dispose()
  }

  val size: Dimension
    get() = content.size

  fun setLocation(screenPoint: Point){
    popup.setLocation(screenPoint)
  }
}