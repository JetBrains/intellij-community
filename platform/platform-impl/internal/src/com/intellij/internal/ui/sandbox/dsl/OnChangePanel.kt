// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class OnChangePanel : UISandboxPanel {

  override val title: String = "OnChange"

  private lateinit var taLog: JBTextArea

  private var checkBoxValue = true
  private var radioButtonValue = true
  private var textFieldValue = "textField"
  private var textAreaValue = "textArea"
  private var comboBoxNotEditableValue = "Item 2"
  private var comboBoxEditableValue: String? = "Item"
  private var expandableTextFieldValue = "Item"
  private var textFieldWithBrowseButtonValue = "textFieldWithBrowseButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        checkBox("checkBox")
          .onChangedContext(::log)
          .bindSelected(::checkBoxValue)
      }
      buttonsGroup {
        row {
          radioButton("true", true)
            .onChangedContext { component, context -> log(component, context, "true") }
          radioButton("false", false)
            .onChangedContext { component, context -> log(component, context, "false") }
        }
      }.bind(::radioButtonValue)
      row {
        textField()
          .onChangedContext(::log)
          .bindText(::textFieldValue)
      }
      row {
        textArea()
          .applyToComponent { rows = 2 }
          .align(AlignX.FILL)
          .onChangedContext(::log)
          .bindText(::textAreaValue)
      }
      row {
        comboBox(listOf("Item 1", "Item 2", "Last"))
          .onChangedContext { component, context -> log(component, context, "notEditable") }
          .bindItem(::comboBoxNotEditableValue.toNullableProperty())
        comboBox(listOf("Item 1", "Item 2", "Last"))
          .applyToComponent { isEditable = true }
          .onChangedContext { component, context -> log(component, context, "editable") }
          .bindItem(::comboBoxEditableValue)
      }
      row {
        dropDownLink("Item 1", listOf("Item 1", "Item 2", "Last"))
          .onChangedContext(::log)
      }
      row {
        expandableTextField()
          .align(AlignX.FILL)
          .onChangedContext(::log)
          .bindText(::expandableTextFieldValue)
      }
      row {
        textFieldWithBrowseButton()
          .align(AlignX.FILL)
          .onChangedContext(::log)
          .bindText(::textFieldWithBrowseButtonValue)
      }

      row {
        taLog = textArea()
          .align(Align.FILL)
          .applyToComponent {
            isEditable = false
          }.component
      }.resizableRow()
    }
  }

  private fun log(component: JComponent, context: ChangeContext, text: String? = null) {
    if (!::taLog.isInitialized) {
      return
    }

    val textLog = if (text == null) "" else "($text)"
    taLog.text += "component = ${component::class.java.name}$textLog, binding = ${context.binding}, event: ${context.event}\n"
  }
}