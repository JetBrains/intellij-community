// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import javax.swing.JRadioButton
import kotlin.reflect.KMutableProperty0

class CheckboxDescriptor(@NlsContexts.Checkbox val name: String,
                         val binding: PropertyBinding<Boolean>,
                         @NlsContexts.DetailedDescription val comment: String? = null,
                         @Nls val groupName: String? = null) {
  constructor(@NlsContexts.Checkbox name: String, mutableProperty: KMutableProperty0<Boolean>,
              @NlsContexts.DetailedDescription comment: String? = null, @Nls groupName: String? = null)
    : this(name, mutableProperty.toBinding(), comment, groupName)

  fun asUiOptionDescriptor(): BooleanOptionDescription = asOptionDescriptor {
    UISettings.instance.fireUISettingsChanged()
  }

  fun asOptionDescriptor(): BooleanOptionDescription = asOptionDescriptor(null)

  fun asOptionDescriptor(fireUpdated: (() -> Unit)?): BooleanOptionDescription {
    val optionName = when {
      groupName != null -> {
        val prefix = groupName.trim().removeSuffix(":")
        "$prefix: $name"
      }
      else -> name
    }
    return object : BooleanOptionDescription(optionName, ID) {
      override fun setOptionState(enabled: Boolean) {
        binding.set(enabled)
        fireUpdated?.invoke()
      }

      override fun isOptionEnabled() = binding.get.invoke()
    }
  }
}

fun Cell.checkBox(ui: CheckboxDescriptor): CellBuilder<JBCheckBox> {
  return checkBox(ui.name, ui.binding.get, ui.binding.set, ui.comment)
}

fun Cell.radioButton(ui: CheckboxDescriptor): CellBuilder<JRadioButton> {
  return radioButton(ui.name, ui.binding.get, ui.binding.set, ui.comment)
}
