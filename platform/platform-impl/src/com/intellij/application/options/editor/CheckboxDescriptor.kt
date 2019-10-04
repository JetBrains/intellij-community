// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import kotlin.reflect.KMutableProperty0

class CheckboxDescriptor(val name: String, val binding: PropertyBinding<Boolean>, val comment : String? = null) {
  constructor(name: String, mutableProperty: KMutableProperty0<Boolean>, comment : String? = null) : this(name, mutableProperty.toBinding(), comment)

  fun asOptionDescriptor() = object : BooleanOptionDescription(name, ID) {
    override fun setOptionState(enabled: Boolean) {
      binding.set(enabled)
      UISettings.instance.fireUISettingsChanged()
    }

    override fun isOptionEnabled() = binding.get.invoke()
  }
}

fun Cell.checkBox(ui: CheckboxDescriptor): CellBuilder<JBCheckBox> {
  return checkBox(ui.name, ui.binding.get, ui.binding.set, ui.comment)
}
