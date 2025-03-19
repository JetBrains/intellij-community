// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl.validation

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class CrossValidationPanel : UISandboxPanel {

  override val title: String = "Cross Validation"

  override fun createContent(disposable: Disposable): JComponent {
    lateinit var result: DialogPanel
    val allValidators = mutableListOf<ComponentValidator>()
    result = panel {
      row {
        text("Kotlin UI DSL doesn't have special API for cross fields validation for now. Below is an example with the base API: any of the following fields should contain unique value, error shown otherwise")
      }

      val allTextFields = mutableListOf<JBTextField>()
      for (i in 1..3) {
        row("Field $i:") {
          val textField = textField()
            .onChanged {
              for (validator in allValidators) {
                validator.revalidate()
              }
            }.component
          val validator = ComponentValidator(disposable)
            .withValidator { validate(textField, allTextFields) }
          validator.installOn(textField)
          allValidators.add(validator)
          allTextFields.add(textField)
        }
      }
    }

    for (validator in allValidators) {
      validator.revalidate()
    }
    return result
  }

  private fun validate(field: JBTextField, allFields: List<JBTextField>): ValidationInfo? {
    val sameFieldsIndexes = allFields.mapIndexedNotNull { index, textField ->
      if (field !== textField && field.text == textField.text) index + 1 else null
    }

    return when (sameFieldsIndexes.size) {
      0 -> null
      1 -> ValidationInfo("Same as field ${sameFieldsIndexes[0]}", field)
      else -> {
        val indexes = sameFieldsIndexes.joinToString(separator = " and ") { it.toString() }
        ValidationInfo("Same as fields $indexes", field)
      }
    }
  }
}