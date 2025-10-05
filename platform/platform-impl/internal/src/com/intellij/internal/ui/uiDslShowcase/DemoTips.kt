// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI

@Suppress("DialogTitleCapitalization")
@Demo(title = "Tips",
  description = "Here are some useful tips and tricks",
  scrollbar = true)
fun demoTips(): DialogPanel {
  val panel = panel {
    row {
      label("Bold text")
        .bold()
    }.rowComment("Use Cell.bold method for bold text, works for any component")

    row {
      textField()
        .columns(COLUMNS_MEDIUM)
    }.rowComment("Configure width of textField, comboBox and textArea by columns method")

    row {
      textField()
        .text("Initialized text")
    }.rowComment("Set initial text of text component by text method")

    row("intTextField(0..1000, 100):") {
      intTextField(0..1000, 100)
        .text("500")
    }.rowComment("Use Row.intTextField for input integer numbers. There is value validation, range validation and " +
                 "supported up/down keys")

    row("Some text") {
      textField()
        .resizableColumn()
      link("Config...") {}
      link("About") {}
    }.rowComment("Use Cell.resizableColumn if column should occupy whole free space. Remaining cells are adjusted to the right")

    row {
      text("If needed text color of Row.text() can be changed to comment color with the following code:<br>" +
           "foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND<br>" +
           "It differs from Row.comment() because comments use smaller font size")
        .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
    }

    group("Use Row.cell() to disable expanding components on whole width") {
      row("Row 1:") {
        textField()
          .gap(RightGap.SMALL)
          .resizableColumn()
          .align(AlignX.FILL)
        val action = object : DumbAwareAction(AllIcons.Actions.QuickfixOffBulb) {
          override fun actionPerformed(e: AnActionEvent) {
          }
        }
        actionButton(action)
      }.layout(RowLayout.PARENT_GRID)

      row("Row 2:") {
        textField()
          .gap(RightGap.SMALL)
          .align(AlignX.FILL)
        cell()
      }.layout(RowLayout.PARENT_GRID)
        .rowComment("Last textField occupies only one column like the previous textField")
    }

    group("""Use Panel.row("") if label is empty""") {
      row("Row 1:") {
        textField()
      }
      row("") {
        textField()
      }
    }
    group("Use Cell.widthGroup to use the same width") {
      row {
        textField().widthGroup("GroupName")
      }
      row {
        button("Button") {}.widthGroup("GroupName")
      }.rowComment("All components from the same width group will have the same width equals to maximum width from the group. Cannot be used together with AlignX.FILL")
    }
    row {
      panel {
        group("No default radio button") {
          var value = 0
          buttonsGroup {
            row {
              radioButton("Value = 1", 1)
            }
            row {
              radioButton("Value = 2", 2)
                .comment("Initial bounded value is 0, RadioButtons in the group are deselected by default", maxLineLength = 40)
            }
          }.bind({ value }, { value = it })
        }
      }.gap(RightGap.COLUMNS)
        .align(AlignY.TOP)
        .resizableColumn()
      panel {
        group("Default radio button") {
          var value = 0
          buttonsGroup {
            row {
              radioButton("Value = 1", 1)
            }
            row {
              radioButton("Value = 2, isSelected = true", 2)
                .selected(true)
                .comment("Initial bounded value is 0, this RadioButton is selected because initial bound variable value is not equal to values of RadioButton in the group and isSelected = true", maxLineLength = 40)
            }
          }.bind({ value }, { value = it })
        }
      }.align(AlignY.TOP)
        .resizableColumn()
    }
  }

  return panel
}
