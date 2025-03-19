// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class JBPasswordFieldPanel : UISandboxPanel {

  override val title: String = "JBPasswordField"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      withStateLabel {
        passwordField()
      }
      withStateLabel {
        passwordField().applyToComponent {
          isEditable = false
          text = "Some text"
        }
      }
      withStateLabel {
        passwordField().applyToComponent {
          isEnabled = false
          text = "Some text"
        }
      }
      row("Without echo char:") {
        passwordField().applyToComponent {
          setEchoChar('\u0000')
        }
      }
      row("With empty text:") {
        passwordField().applyToComponent {
          emptyText.text = "Type some text"
        }
      }
    }
  }
}