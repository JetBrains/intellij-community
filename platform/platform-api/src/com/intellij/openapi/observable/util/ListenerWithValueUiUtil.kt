// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListenerWithValueUiUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.DropDownLink
import org.jetbrains.annotations.ApiStatus
import java.awt.ItemSelectable
import java.awt.event.ItemEvent
import javax.swing.JComboBox
import javax.swing.text.Document
import javax.swing.text.JTextComponent

fun <T> JComboBox<T>.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  (this as ItemSelectable).whenItemSelected(parentDisposable, listener)
}

fun <T> DropDownLink<T>.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  (this as ItemSelectable).whenItemSelected(parentDisposable, listener)
}

@ApiStatus.Internal
fun <T> ItemSelectable.whenItemSelected(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
  whenStateChanged(parentDisposable) { event ->
    if (event.stateChange == ItemEvent.SELECTED) {
      @Suppress("UNCHECKED_CAST")
      listener(event.item as T)
    }
  }
}

fun TextFieldWithBrowseButton.whenTextChanged(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
  textField.whenTextChanged(parentDisposable, listener)
}

fun SearchTextField.whenTextChanged(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
  textEditor.whenTextChanged(parentDisposable, listener)
}

fun JTextComponent.whenTextChanged(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
  document.whenTextChanged(parentDisposable, listener)
}

fun Document.whenTextChanged(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
  whenDocumentChanged(parentDisposable) {
    listener(getText(0, length))
  }
}
