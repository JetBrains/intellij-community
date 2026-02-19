// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class LabelsPanel : UISandboxPanel {

  override val title: String = "Labels"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row("Row1:") {
        textField()
      }

      group("RadioButton") {
        buttonsGroup {
          row("Row1:") {
            radioButton("radioButton")
          }
        }
        row("Row2:") {
          textField()
        }
      }

      group("CheckBox") {
        row("Row1:") {
          checkBox("checkBox")
        }
        row("Row2:") {
          textField()
        }
      }

      group("CheckBox") {
        row("Row1:") {
          checkBox("checkBox")
        }
        row("Row2 Long label:") {
          textField()
        }
      }

      group("layout(RowLayout.INDEPENDENT)") {
        row("Row1:") {
          checkBox("checkBox")
        }.layout(RowLayout.INDEPENDENT)
        row("Row2:") {
          textField()
        }.layout(RowLayout.INDEPENDENT)
      }

      group("Cell.label") {
        row {
          checkBox("checkBox")
            .label("Row1:")
        }
        row {
          textField()
            .label("Row2:")
        }
      }
    }
  }
}