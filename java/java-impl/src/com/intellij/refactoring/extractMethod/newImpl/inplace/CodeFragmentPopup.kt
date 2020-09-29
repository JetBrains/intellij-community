// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.hint.EditorFragmentComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.popup.PopupComponent
import java.awt.FlowLayout
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

class CodeFragmentPopup(val editor: Editor, val lines: IntRange, private val onClick: Runnable): Disposable {

  private val content = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).also { panel ->
    panel.add(createEditorFragment(editor, lines))
    panel.addMouseListener(object: MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        onClick.run()
      }
    })
  }

  private val popup = createFragmentPopup(content)

  private val wrapper = PopupComponent.DialogPopupWrapper(editor.component, content, 0, 0, popup)

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
    content.removeAll()
    content.add(createEditorFragment(editor, lines))
    wrapper.window.pack()
    wrapper.window.repaint()
  }

  fun show() {
    wrapper.setRequestFocus(false)
    wrapper.show()
  }

  fun hide() {
    wrapper.hide(false)
  }

  override fun dispose() {
    wrapper.hide(true)
    wrapper.window.dispose()
  }

  val window: Window
    get() = wrapper.window
}