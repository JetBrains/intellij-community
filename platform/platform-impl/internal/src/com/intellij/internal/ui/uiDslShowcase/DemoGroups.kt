// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel

@Suppress("DialogTitleCapitalization")
@Demo(title = "Groups",
  description = "There are several useful methods that group parts of content in one group. Some of them mention 'own grid inside' " +
                "which means labels and other cells in such groups are not aligned with parent labels and cells",
  scrollbar = true)
fun demoGroups(): DialogPanel {
  return panel {
    panel {
      row("Panel.panel row:") {
        textField()
      }.rowComment("Panel.panel method creates sub-panel that occupies whole width and uses own grid inside")
    }

    rowsRange {
      row("Panel.rowsRange row:") {
        textField()
      }.rowComment("Panel.rowsRange is similar to Panel.panel but uses the same grid as parent. " +
                   "Useful when grouped content should be managed together with RowsRange.enabledIf for example")
    }

    group("Panel.group") {
      row("Panel.group row:") {
        textField()
      }.rowComment("Panel.group adds panel with independent grid, title and some vertical space before/after the group")
    }

    groupRowsRange("Panel.groupRowsRange") {
      row("Panel.groupRowsRange row:") {
        textField()
      }.rowComment("Panel.groupRowsRange is similar to Panel.group but uses the same grid as parent. " +
                   "See how aligned Panel.rowsRange row")
    }

    collapsibleGroup("Panel.collapsible&Group") {
      row("Panel.collapsibleGroup row:") {
        textField()
      }.rowComment("Panel.collapsibleGroup adds collapsible panel with independent grid, title and some vertical " +
                   "space before/after the group. The group title is focusable via the Tab key and supports mnemonics")
    }

    var value = true
    buttonsGroup("Panel.buttonGroup:") {
      row {
        radioButton("true", true)
      }
      row {
        radioButton("false", false)
      }.rowComment("Panel.buttonGroup unions Row.radioButton in one group. Must be also used for Row.checkBox if they are grouped " +
                   "with some title")
    }.bind({ value }, { value = it })

    separator()
      .rowComment("Use separator() for horizontal separator")

    row {
      label("Use Row.panel for creating panel in a cell:")
      panel {
        row("Sub panel row 1:") {
          textField()
        }
        row("Sub panel row 2:") {
          textField()
        }
      }
    }
  }
}
