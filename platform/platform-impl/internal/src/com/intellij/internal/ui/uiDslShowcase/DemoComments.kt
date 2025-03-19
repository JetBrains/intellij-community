// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text

@Demo(title = "Comments",
      description = "Comment is a gray (depends on color scheme) text which can be assigned to cell (placed under cell), rows or placed in any cell directly")
fun demoComments(): DialogPanel {
  return panel {
    row("Row1:") {
      textField()
        .comment("Comment to textField1")
        .text("textField1")
      textField()
        .text("textField2")
      textField()
        .comment("Comment to textField3")
        .text("textField3")
    }

    row("Row2:") {
      textField()
    }.rowComment("Row2 comment is placed under the row")

    row("Row3:") {
      comment(
        "Comments can be an html text with some clickable <a href='link'>link</a> and even <a href='several links'>several links</a>.<br><icon src='AllIcons.General.Information'>&nbsp;It's possible to use line breaks and bundled icons") {
        Messages.showMessageDialog("Link '${it.description}' is clicked", "Message", null)
      }
    }

    val longString = (1..8).joinToString { "A very long string" }

    row("Row4:") {
      comment("Comments use MAX_LINE_LENGTH_WORD_WRAP by default<br>$longString")
    }


    row("Row5:") {
      comment("Long comments can deny word wrapping by MAX_LINE_LENGTH_NO_WRAP<br>$longString", maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
    }

    row("Row6:") {
      comment("Long comments can use maxLineLength, here it is 100<br>$longString", maxLineLength = 100)
    }
  }
}
