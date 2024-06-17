// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.EdtDataRule
import com.intellij.openapi.actionSystem.PlatformDataKeys.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.pom.Navigatable
import com.intellij.ui.EditorTextField

internal class BasicEdtDataRule : EdtDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    // editor
    val editor = snapshot[EDITOR]
    if (editor is EditorEx) {
      sink[COPY_PROVIDER] = editor.getCopyProvider()
      sink[PASTE_PROVIDER] = editor.getPasteProvider()
      sink[CUT_PROVIDER] = editor.getCutProvider()
      sink[DELETE_ELEMENT_PROVIDER] = editor.getDeleteProvider()
    }
    if (editor != null && snapshot[FILE_EDITOR] == null &&
        editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY) != true) {
      sink[FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(editor)
    }

    // navigatables
    val items = snapshot[SELECTED_ITEMS]
    val navigatables = items?.filterIsInstance<Navigatable>()
    if (navigatables?.isNotEmpty() == true) {
      sink[NAVIGATABLE_ARRAY] = navigatables.toTypedArray()
    }
    else {
      snapshot[NAVIGATABLE]?.let {
        sink[NAVIGATABLE_ARRAY] = arrayOf(it)
      }
    }
  }
}