// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.hint.EditorFragmentComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.popup.PopupComponent
import java.awt.GridBagLayout
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import java.awt.GridBagConstraints

class CodeFragmentPopup(val editor: Editor, val lines: IntRange, private val onClick: Runnable): Disposable {

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
        onClick.run()
      }
    })
  }

  private val popupWrapper = PopupComponent.DialogPopupWrapper(editor.component, content, 0, 0, createFragmentPopup(content))

  private fun createFragmentPopup(content: JPanel): JBPopup {
    return JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
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
    window.preferredSize = editorFragmentComponent.preferredSize
    window.validate()
  }

  fun show() {
    if (! window.isVisible) {
      popupWrapper.setRequestFocus(false)
      popupWrapper.show()
    }
  }

  fun hide() {
    popupWrapper.hide(false)
  }

  override fun dispose() {
    popupWrapper.hide(true)
    popupWrapper.window.dispose()
  }

  val window: Window
    get() = popupWrapper.window
}