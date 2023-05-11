// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.util.Disposer

internal class FloatingCodeToolbarEditorCustomizer : TextEditorCustomizer {
  override fun customize(textEditor: TextEditor) {
    val toolbar = CodeFloatingToolbar(textEditor.editor)
    Disposer.register(textEditor, toolbar)
  }

}
