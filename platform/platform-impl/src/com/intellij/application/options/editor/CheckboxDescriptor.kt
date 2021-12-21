// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
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

  fun asUiOptionDescriptor(): BooleanOptionDescription {
    return asOptionDescriptor {
      UISettings.instance.fireUISettingsChanged()
    }
  }

  fun asOptionDescriptor(): BooleanOptionDescription = asOptionDescriptor(null)

  fun asOptionDescriptor(fireUpdated: (() -> Unit)?): BooleanOptionDescription {
    val optionName = if (groupName == null) {
      name
    }
    else {
      "${groupName.trim().removeSuffix(":")}: $name"
    }
    return object : BooleanOptionDescription(optionName, EDITOR_TABS_OPTIONS_ID) {
      override fun setOptionState(enabled: Boolean) {
        binding.set(enabled)
        fireUpdated?.invoke()
      }

      override fun isOptionEnabled() = binding.get.invoke()
    }
  }
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun Cell.checkBox(ui: CheckboxDescriptor): CellBuilder<JBCheckBox> {
  return checkBox(ui.name, ui.binding.get, ui.binding.set, ui.comment)
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun Cell.radioButton(ui: CheckboxDescriptor): CellBuilder<JRadioButton> {
  return radioButton(ui.name, ui.binding.get, ui.binding.set, ui.comment)
}

fun Row.checkBox(ui: CheckboxDescriptor): com.intellij.ui.dsl.builder.Cell<JBCheckBox> {
  val result = checkBox(ui.name)
    .bindSelected(ui.binding)
  ui.comment?.let { result.comment(it) }
  return result
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun Row.radioButton(ui: CheckboxDescriptor): com.intellij.ui.dsl.builder.Cell<JBRadioButton> {
  val result = radioButton(ui.name)
    .bindSelected(ui.binding)
  ui.comment?.let { result.comment(it) }
  return result
}
