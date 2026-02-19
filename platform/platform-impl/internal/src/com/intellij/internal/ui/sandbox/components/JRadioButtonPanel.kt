// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.applyStateText
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent

internal class JRadioButtonPanel : UISandboxPanel {

  override val title: String = "JRadioButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("States") {
        buttonsGroup {
          row {
            radioButton("")
              .comment(
                "To focus radioButton without selection press left mouse button, move mouse outside of the radioButton and release the button")
              .applyStateText()
          }
          row {
            radioButton("")
              .selected(true)
              .applyStateText()
          }
        }

        buttonsGroup {
          for (isSelected in listOf(false, true)) {
            row {
              radioButton("")
                .selected(isSelected)
                .enabled(false)
                .applyStateText()
            }
          }
        }
      }
    }
  }
}