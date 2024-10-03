// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class TextFieldsPanel: UISandboxPanel {

  override val title: String = "Text Fields"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      row("Text field 1:") {
        textField()
          .columns(10)
          .comment("columns = 10")
      }
      row("Text field 2:") {
        textField()
          .align(AlignX.FILL)
          .comment("align(AlignX.FILL)")
      }
      row("Int text field 1:") {
        intTextField()
          .columns(10)
          .comment("columns = 10")
      }
      row("Int text field 2:") {
        intTextField(range = 0..1000)
          .comment("range = 0..1000")
      }
      row("Int text field 2:") {
        intTextField(range = 0..1000, keyboardStep = 100)
          .comment("range = 0..1000, keyboardStep = 100")
      }
    }

    result.registerValidators(disposable)

    return result
  }
}