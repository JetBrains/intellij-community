// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.Alarm
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

@Suppress("DialogTitleCapitalization")
@Demo(title = "Binding",
      description = "It is possible to bind component values to properties. Such properties are applied only when DialogPanel.apply is invoked. " +
                    "Methods DialogPanel.isModified and DialogPanel.reset are also supported automatically for bound properties",
      scrollbar = true)
fun demoBinding(parentDisposable: Disposable): DialogPanel {
  lateinit var lbIsModified: JLabel
  lateinit var lbModel: JLabel
  lateinit var panel: DialogPanel
  val alarm = Alarm(parentDisposable)
  val model = Model()

  fun initValidation() {
    alarm.addRequest(Runnable {
      val modified = panel.isModified()
      lbIsModified.text = "isModified: $modified"
      lbIsModified.bold(modified)
      lbModel.text = "<html>$model"

      initValidation()
    }, 1000)
  }

  panel = panel {
    row {
      checkBox("Checkbox")
        .bindSelected(model::checkbox)
    }
    row("textField:") {
      textField()
        .bindText(model::textField)
    }
    row("intTextField(0..100):") {
      intTextField()
        .bindIntText(model::intTextField)
    }
    row("comboBox:") {
      comboBox(Color.entries)
        .bindItem(model::comboBoxColor.toNullableProperty())
    }
    row("slider:") {
      slider(0, 100, 10, 50)
        .bindValue(model::slider)
    }
    row("spinner:") {
      spinner(0..100)
        .bindIntValue(model::spinner)
    }
    buttonsGroup("radioButton:") {
      for (value in Color.entries) {
        row {
          radioButton(value.name, value)
        }
      }
    }.bind(model::radioButtonColor)

    group("DialogPanel Control") {
      row {
        button("Reset") {
          panel.reset()
        }
        button("Apply") {
          panel.apply()
        }
        lbIsModified = label("").component
      }
      row {
        lbModel = label("").component
      }
    }
  }

  SwingUtilities.invokeLater {
    initValidation()
  }

  return panel
}

private fun JComponent.bold(isBold: Boolean) {
  font = font.deriveFont(if (isBold) Font.BOLD else Font.PLAIN)
}

@ApiStatus.Internal
internal data class Model(
  var checkbox: Boolean = false,
  var textField: String = "",
  var intTextField: Int = 0,
  var comboBoxColor: Color = Color.GREY,
  var slider: Int = 0,
  var spinner: Int = 0,
  var radioButtonColor: Color = Color.GREY,
)

@ApiStatus.Internal
internal enum class Color {
  WHITE,
  GREY,
  BLACK
}
