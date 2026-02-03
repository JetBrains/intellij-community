// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.tests.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class ContextHelpTestPanel : UISandboxPanel {

  override val title: String = "Context Help"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        textField()
          .align(AlignX.CENTER)
          .contextHelp("AlignX.CENTER")
      }
      row {
        textField()
          .align(AlignX.RIGHT)
          .commentRight("Comment right")
          .contextHelp("AlignX.RIGHT")
      }
      group("Test Enabled/Visible") {
        lateinit var cbEnabled: JCheckBox
        lateinit var cbVisible: JCheckBox
        row {
          cbEnabled = checkBox("Enabled")
            .selected(true)
            .component
          cbVisible = checkBox("Visible")
            .selected(true)
            .component
        }
        row {
          textField()
            .align(AlignX.FILL)
            .comment("Ordinary comment")
            .commentRight("Comment right")
            .contextHelp("AlignX.FILL, <b>bold</b> text", "Title <b>BOLD</b>")
            .enabledIf(cbEnabled.selected)
            .visibleIf(cbVisible.selected)
        }
      }
      row {
        textField()
          .align(Align.CENTER)
          .commentRight("Comment right")
          .contextHelp("Align.CENTER + resizableRow")
      }.resizableRow()
      row {
        textField()
          .align(AlignX.FILL + AlignY.BOTTOM)
          .comment("Ordinary comment")
          .commentRight("Comment right")
          .contextHelp("AlignX.RIGHT + AlignY.BOTTOM + resizableRow<br>second line<br>third line")
      }.resizableRow()
    }
  }
}