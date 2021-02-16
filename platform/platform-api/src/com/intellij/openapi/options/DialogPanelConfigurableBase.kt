// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.ui.ComboBox
import javax.swing.JTextField
import javax.swing.JToggleButton

abstract class DialogPanelConfigurableBase : DialogPanelUnnamedConfigurableBase(), Configurable {

  final override fun isModified(textField: JTextField, value: String): Boolean {
    return super<Configurable>.isModified(textField, value)
  }

  final override fun isModified(textField: JTextField, value: Int, range: UINumericRange): Boolean {
    return super<Configurable>.isModified(textField, value, range)
  }

  final override fun isModified(toggleButton: JToggleButton, value: Boolean): Boolean {
    return super<Configurable>.isModified(toggleButton, value)
  }

  final override fun <T> isModified(comboBox: ComboBox<T>, value: T): Boolean {
    return super<Configurable>.isModified(comboBox, value)
  }
}
