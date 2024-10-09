// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ListenerWithValueUiUtil")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.SearchTextField
import javax.swing.text.Document
import javax.swing.text.JTextComponent

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
