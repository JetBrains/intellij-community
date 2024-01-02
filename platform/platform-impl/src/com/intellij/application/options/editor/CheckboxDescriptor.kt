// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.reflect.KMutableProperty0

class CheckboxDescriptor(val name: @NlsContexts.Checkbox String,
                         @ApiStatus.Internal val getter: () -> Boolean,
                         @ApiStatus.Internal val setter: (value: Boolean) -> Unit,
                         internal val comment: @NlsContexts.DetailedDescription String? = null,
                         internal val groupName: @Nls String? = null) {
  constructor(name: @NlsContexts.Checkbox String,
              mutableProperty: KMutableProperty0<Boolean>,
              comment: @NlsContexts.DetailedDescription String? = null,
              groupName: @Nls String? = null)
    : this(name, { mutableProperty.get() }, { mutableProperty.set(it) }, comment, groupName)

  fun asUiOptionDescriptor(): BooleanOptionDescription {
    return asOptionDescriptor {
      UISettings.getInstance().fireUISettingsChanged()
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
        setter(enabled)
        fireUpdated?.invoke()
      }

      override fun isOptionEnabled() = getter.invoke()
    }
  }
}

@ApiStatus.ScheduledForRemoval
@ApiStatus.Internal
@Deprecated("Use Kotlin UI DSL Version 2")
fun Cell.checkBox(ui: CheckboxDescriptor): CellBuilder<JBCheckBox> {
  return checkBox(ui.name, ui.getter, ui.setter, ui.comment)
}

fun Row.checkBox(ui: CheckboxDescriptor): com.intellij.ui.dsl.builder.Cell<JBCheckBox> {
  val result = checkBox(ui.name)
    .bindSelected(ui.getter, ui.setter)
  ui.comment?.let { result.comment(it) }
  return result
}
