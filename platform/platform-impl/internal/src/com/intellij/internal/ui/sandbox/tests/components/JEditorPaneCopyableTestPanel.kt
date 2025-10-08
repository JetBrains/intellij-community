// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.tests.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.setCopyable
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent
import javax.swing.JEditorPane

internal class JEditorPaneCopyableTestPanel : UISandboxPanel {

  override val title: String = "JEditorPane.setCopyable"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var text: Cell<JEditorPane>
      lateinit var commentText: Cell<JEditorPane>
      row {
        checkBox("Copyable")
          .selected(true)
          .onChanged {
            text.setCopyable(it.isSelected)
            commentText.setCopyable(it.isSelected)
          }
      }

      row {
        text = text("Some copyable or not ${"long ".repeat(50)}text")
          .comment("Some copyable or not ${"long ".repeat(20)}comment")
        text.setCopyable(true)
      }

      row {
        commentText = comment("Some copyable or not ${"long ".repeat(50)}comment")
        commentText.setCopyable(true)
      }

      row {
        textField()
          .comment("Check that test selection is hidden after focusing this text field")
      }
    }
  }

  private fun Cell<JEditorPane>.setCopyable(copyable: Boolean) {
    component.setCopyable(copyable)
    comment?.setCopyable(copyable)
  }
}