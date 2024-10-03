// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.SearchTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import javax.swing.*
import javax.swing.text.JTextComponent

internal fun getStateText(vararg states: @Nls String?): @Nls String {
  return states.filter { !it.isNullOrBlank() }.joinToString()
}

internal fun getStateText(component: JComponent, vararg additionalStates: @Nls String?): @Nls String {
  var isEnabled = component.isEnabled
  var componentSpecificStates = emptyArray<String?>()

  when (component) {
    is JButton -> {
      componentSpecificStates = arrayOf(
        if (component.getClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY) == true) "Default" else null
      )
    }
    is ThreeStateCheckBox -> {
      val state = when (component.state) {
        ThreeStateCheckBox.State.NOT_SELECTED -> "Not selected"
        ThreeStateCheckBox.State.DONT_CARE -> "Indeterminate"
        ThreeStateCheckBox.State.SELECTED -> "Selected"
        null -> "null"
      }

      componentSpecificStates = arrayOf(state)
    }
    is JToggleButton -> {
      componentSpecificStates = arrayOf(
        if (component.isSelected) "Selected" else "Not selected",
      )
    }
    is JComboBox<*> -> {
      componentSpecificStates = arrayOf(
        if (component.isEditable) "Editable" else "Not editable"
      )
    }
    is JTextComponent -> {
      componentSpecificStates = arrayOf(
        if (component.isEditable) "Editable" else "Not editable"
      )
    }
    is SearchTextField -> {
      isEnabled = component.textEditor.isEnabled
    }
  }

  val states = arrayOf(
    if (isEnabled) "Enabled" else "Disabled",
    *componentSpecificStates,
    *additionalStates
  )
  return getStateText(*states)
}

internal fun Cell<AbstractButton>.applyStateText() {
  component.text = getStateText(component)
}

internal fun <T : JComponent> Panel.withStateLabel(vararg additionalStates: @Nls String?, init: Row.() -> Cell<T>) {
  val label = JLabel()

  row(label) {
    val cell = init()
    label.text = getStateText(cell.component, *additionalStates) + ":"
  }
}

internal fun <T : JTextArea> Cell<T>.initWithText(): Cell<T> {
  align(AlignX.FILL)
  rows(5)
  component.addText()

  return this
}

internal fun <T : JTextArea> T.addText() {
  text = (1..20).joinToString(separator = "\n") { "Line $it" }
}
