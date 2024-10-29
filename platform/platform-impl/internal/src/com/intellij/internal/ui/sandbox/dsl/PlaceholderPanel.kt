// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.Alarm
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

@Suppress("DialogTitleCapitalization")
internal class PlaceholderPanel : UISandboxPanel {

  override val title: String = "Placeholder"

  private val model = Model()

  private val customTextEdit = panel {
    row {
      intTextField(0..100)
        .bindIntText(model::placeholderInstanceTextField)
        .errorOnApply("Int should be positive") {
          (it.text.toIntOrNull() ?: 0) <= 0
        }
    }
  }

  private lateinit var lbIsModified: JLabel
  private lateinit var lbValidation: JLabel
  private lateinit var lbModel: JLabel

  override fun createContent(disposable: Disposable): JComponent {
    lateinit var placeholder: Placeholder
    lateinit var result: DialogPanel

    result = panel {
      row {
        text("Validation of placeholder. Select component and change values. Reset, Apply and isModified should work " +
             "as expected. Check also validation for int text field")
      }.bottomGap(BottomGap.MEDIUM)

      row {
        checkBox("simpleCheckbox")
          .bindSelected(model::simpleCheckbox)
      }
      row("Select Placeholder:") {
        comboBox(PlaceholderComponent.entries.toList())
          .onChanged {
            val type = it.item
            if (type == null) {
              placeholder.component = null
            }
            else {
              placeholder.component = createPlaceholderComponent(type)
            }
          }
        checkBox("enabled")
          .selected(true)
          .onChanged { placeholder.enabled(it.isSelected) }
        checkBox("visible")
          .selected(true)
          .onChanged { placeholder.visible(it.isSelected) }
      }
      row("Placeholder:") {
        placeholder = placeholder()
      }

      group("DialogPanel Control") {
        row {
          button("Reset") {
            result.reset()
          }
          button("Apply") {
            result.apply()
          }
          lbIsModified = label("").component
        }
        row {
          lbValidation = label("").component
        }
        row {
          lbModel = label("").component
        }
      }
    }

    result.registerValidators(disposable)
    val alarm = Alarm(disposable)

    SwingUtilities.invokeLater {
      initValidation(alarm, result)
    }

    return result
  }

  private fun initValidation(alarm: Alarm, panel: DialogPanel) {
    fun JComponent.bold(isBold: Boolean) {
      font = font.deriveFont(if (isBold) Font.BOLD else Font.PLAIN)
    }

    alarm.addRequest(Runnable {
      val validationErrors = panel.validateCallbacks.mapNotNull { it() }
      val modified = panel.isModified()

      lbIsModified.text = "isModified: $modified"
      lbIsModified.bold(modified)
      lbValidation.text = "<html>Validation Errors: ${validationErrors.joinToString { it.message }}"
      lbValidation.bold(validationErrors.isNotEmpty())
      lbModel.text = "<html>$model"

      initValidation(alarm, panel)
    }, 1000)
  }

  private fun createPlaceholderComponent(placeholderComponent: PlaceholderComponent): JComponent? {
    return when (placeholderComponent) {
      PlaceholderComponent.NONE -> null
      PlaceholderComponent.LABEL -> JLabel("JLabel")
      PlaceholderComponent.CHECK_BOX -> panel {
        row { checkBox("placeholderCheckBox").bindSelected(model::placeholderCheckBox) }
      }
      PlaceholderComponent.TEXT_FIELD -> panel {
        row { textField().bindText(model::placeholderTextField) }
      }
      PlaceholderComponent.INT_TEXT_FIELD -> panel {
        row { intTextField(0..100).bindIntText(model::placeholderIntTextField) }
      }
      PlaceholderComponent.CUSTOM_TEXT_FIELD -> panel {
        row {
          textField()
            .bindText(model::placeholderCustomTextField)
            .errorOnApply("String should be placeholderCustomTextField") {
              it.text != "placeholderCustomTextField"
            }
        }
      }
      PlaceholderComponent.INSTANCE_TEXT_FIELD -> customTextEdit
    }
  }
}

private enum class PlaceholderComponent {
  NONE,
  LABEL,
  CHECK_BOX,
  TEXT_FIELD,
  INT_TEXT_FIELD,

  /**
   * Custom validation is used
   */
  CUSTOM_TEXT_FIELD,

  /**
   * Used one instance. Check that
   * - all listeners are removed after the component is removed from placeholder
   * - no duplicate listeners in the instance after several installing into placeholder
   */
  INSTANCE_TEXT_FIELD,
}

private data class Model(
  var simpleCheckbox: Boolean = false,
  var placeholderCheckBox: Boolean = false,
  var placeholderTextField: String = "placeholderTextField",
  var placeholderIntTextField: Int = 0,
  var placeholderCustomTextField: String = "placeholderCustomTextField",
  var placeholderInstanceTextField: Int = 0,
)
