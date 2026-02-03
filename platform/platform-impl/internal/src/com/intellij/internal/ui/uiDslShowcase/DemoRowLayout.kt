// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text

@Demo(title = "Row Layout",
      description = "Every row has one of three possible RowLayout. Default value is LABEL_ALIGNED when label is provided for the row, " +
                    "INDEPENDENT otherwise. See documentation for Row.layout method and RowLayout enum")
fun demoRowLayout(): DialogPanel {
  return panel {
    row("PARENT_GRID is set, cell[0,0]:") {
      label("Label 1 in parent grid, cell[1,0]")
      label("Label 2 in parent grid, cell[2,0]")
    }.layout(RowLayout.PARENT_GRID)

    row("PARENT_GRID is set:") {
      textField().text("textField1")
      textField().text("textField2")
    }.layout(RowLayout.PARENT_GRID)

    row("Row label provided, LABEL_ALIGNED is used:") {
      textField().text("textField1")
      textField().text("textField2")
    }

    row {
      label("Row label is not provided, INDEPENDENT is used:")
      textField().text("textField1")
      textField().text("textField2")
    }
  }
}
