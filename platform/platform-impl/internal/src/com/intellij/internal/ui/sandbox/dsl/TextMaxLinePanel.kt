// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent

internal class TextMaxLinePanel : UISandboxPanel {

  override val title: String = "Text MaxLine"

  override val isScrollbarNeeded: Boolean = false

  override fun createContent(disposable: Disposable): JComponent {
    val longLine = (1..4).joinToString { "A very long string with a <a>link</a>" }
    val string = "$longLine<br>$longLine"
    return panel {
      row("comment(string):") {
        comment(string)
      }
      row("comment(string, DEFAULT_COMMENT_WIDTH):") {
        comment(string, maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
      row("comment(string, MAX_LINE_LENGTH_NO_WRAP):") {
        comment(string, maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
      }
      row("text(string):") {
        text(string)
      }
      row("text(string, DEFAULT_COMMENT_WIDTH):") {
        text(string, maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
      row("text(string, MAX_LINE_LENGTH_NO_WRAP):") {
        text(string, maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
      }
    }.apply {
      minimumSize = JBDimension(200, 100)
      preferredSize = JBDimension(200, 100)
    }
  }
}