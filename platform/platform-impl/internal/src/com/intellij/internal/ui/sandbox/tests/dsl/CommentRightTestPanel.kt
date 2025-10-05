// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.tests.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

internal class CommentRightTestPanel : UISandboxPanel {

  override val title: String = "Comment Right"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        textField()
          .align(AlignX.CENTER)
          .commentRight("AlignX.CENTER")
      }
      row {
        textField()
          .align(AlignX.RIGHT)
          .commentRight("AlignX.RIGHT")
      }
      row {
        textField()
          .align(AlignX.FILL)
          .commentRight("AlignX.FILL")
      }
      row {
        textField()
          .align(Align.CENTER)
          .commentRight("Align.CENTER + resizableRow")
      }.resizableRow()
      row {
        textField()
          .align(AlignX.RIGHT + AlignY.BOTTOM)
          .commentRight("AlignX.RIGHT + AlignY.BOTTOM + resizableRow<br>second line<br>third line")
      }.resizableRow()
    }
  }
}