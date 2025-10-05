// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text

@Demo(title = "Comments",
      description = "Comment is a gray (depends on color scheme) small text. There are three types of comments",
      scrollbar = true)
fun demoComments(): DialogPanel {
  return panel {
    group("Cell Comment") {
      row {
        text("Comments related to a cell must be assigned directly to that cell. This ensures proper layout placement and improves support for the accessibility framework")
      }

      row {
        textField()
          .text("textField1")
          .commentRight("Right comment to textField1")
        textField()
          .text("textField2")
          .comment("Bottom comment to textField2")
      }
    }

    group("Row Comment") {
      row("Label:") {
        textField()
      }.rowComment("A row comment is placed below the row")
    }

    group("Arbitrary Comment") {
      row {
        comment("Arbitrary comments can be placed anywhere. They are not related to any cell or row")
      }
    }

    group("Common Info") {
      row {
        comment(
          "Comments can be an html text with some clickable <a href='link'>link</a> and even <a href='several links'>several links</a>.<br><icon src='AllIcons.General.Information'>&nbsp;It's possible to use line breaks and bundled icons") {
          Messages.showMessageDialog("Link '${it.description}' is clicked", "Message", null)
        }
      }

      val longString = (1..8).joinToString { "A very long string" }

      row {
        comment("Comments with MAX_LINE_LENGTH_WORD_WRAP are wrapped automatically<br>$longString")
      }


      row {
        comment("Comments can deny word wrapping by MAX_LINE_LENGTH_NO_WRAP", maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
      }

      row {
        comment("Long comments can use maxLineLength, here it is 60<br>$longString", maxLineLength = 60)
      }
    }
  }
}
