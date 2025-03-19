// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JLabel

@Suppress("DialogTitleCapitalization")
@Demo(title = "Components",
  description = "There are many different components supported by UI DSL. Here are some of them.",
  scrollbar = true)
fun demoComponents(): DialogPanel {
  val panel = panel {
    row {
      checkBox("checkBox")
    }

    row {
      threeStateCheckBox("threeStateCheckBox")
    }

    var radioButtonValue = 2
    buttonsGroup {
      row("radioButton") {
        radioButton("Value 1", 1)
        radioButton("Value 2", 2)
      }
    }.bind({ radioButtonValue }, { radioButtonValue = it })

    row {
      button("button") {}
    }

    row("actionButton:") {
      val action = object : DumbAwareAction("Action text", "Action description", AllIcons.Actions.QuickfixOffBulb) {
        override fun actionPerformed(e: AnActionEvent) {
        }
      }
      actionButton(action)
    }

    row("actionsButton:") {
      actionsButton(object : DumbAwareAction("Action one") {
        override fun actionPerformed(e: AnActionEvent) {
        }
      },
        object : DumbAwareAction("Action two") {
          override fun actionPerformed(e: AnActionEvent) {
          }
        })
    }

    row("segmentedButton:") {
      segmentedButton(listOf("Button 1", "Button 2", "Button Last")) { text = it }.apply {
        selectedItem = "Button 2"
      }
    }

    row("tabbedPaneHeader:") {
      tabbedPaneHeader(listOf("Tab 1", "Tab 2", "Last Tab"))
    }

    row("label:") {
      label("Some label")
    }

    row("text:") {
      text("text supports max line width and can contain links, try <a href='https://www.jetbrains.com'>jetbrains.com</a>.<br><icon src='AllIcons.General.Information'>&nbsp;It's possible to use line breaks and bundled icons")
    }

    row("link:") {
      link("Focusable link") {}
    }

    row("browserLink:") {
      browserLink("jetbrains.com", "https://www.jetbrains.com")
    }

    row("dropDownLink:") {
      dropDownLink("Item 1", listOf("Item 1", "Item 2", "Item 3"))
    }

    row("icon:") {
      icon(AllIcons.Actions.QuickfixOffBulb)
    }

    row("contextHelp:") {
      contextHelp("contextHelp description", "contextHelp title")
    }

    row("textField:") {
      textField()
    }

    row("passwordField:") {
      passwordField().applyToComponent { text = "password" }
    }

    row("textFieldWithBrowseButton:") {
      textFieldWithBrowseButton()
    }

    row("expandableTextField:") {
      expandableTextField()
    }

    row("intTextField(0..100):") {
      intTextField(0..100)
    }

    row("spinner(0..100):") {
      spinner(0..100)
    }

    row("spinner(0.0..100.0, 0.01):") {
      spinner(0.0..100.0, 0.01)
    }

    row("spinner(0.0..100.0, 0.01):") {
      slider(0, 10, 1, 5)
        .labelTable(mapOf(
          0 to JLabel("0"),
          5 to JLabel("5"),
          10 to JLabel("10"),
        ))
    }

    row {
      label("textArea:")
        .align(AlignY.TOP)
        .gap(RightGap.SMALL)
      textArea()
        .rows(5)
        .align(AlignX.FILL)
    }.layout(RowLayout.PARENT_GRID)

    row("comboBox:") {
      comboBox(listOf("Item 1", "Item 2"))
    }
  }

  return panel
}
