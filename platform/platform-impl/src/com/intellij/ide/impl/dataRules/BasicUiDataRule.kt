// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.PlatformDataKeys.*
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ui.getParentOfType
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.ui.EditorTextField

internal class BasicUiDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val component = snapshot[CONTEXT_COMPONENT]
    // editor
    val editor = snapshot[EDITOR]
    ((editor ?: component?.getParentOfType<EditorComponentImpl>()?.editor) as? EditorEx)?.let { editor ->
      sink[COPY_PROVIDER] = editor.getCopyProvider()
      if (!editor.isViewer()) {
        sink[PASTE_PROVIDER] = editor.getPasteProvider()
        sink[CUT_PROVIDER] = editor.getCutProvider()
        sink[DELETE_ELEMENT_PROVIDER] = editor.getDeleteProvider()
      }
    }
    if (editor != null && editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY) != true) {
      val fileEditor = snapshot[FILE_EDITOR]
      if (fileEditor == null) {
        // not applied per-snapshot if fileEditor is present
        sink[FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(editor)
      }
    }
    // navigatables
    val items = snapshot[SELECTED_ITEMS]
    val navigatables = items?.filterIsInstance<Navigatable>()
    if (navigatables?.isNotEmpty() == true) {
      // do not provide PSI in EDT, errors are already logged
      if (navigatables.first() !is PsiElement) {
        sink[NAVIGATABLE_ARRAY] = navigatables.toTypedArray()
      }
    }
    else {
      val navigatable = snapshot[NAVIGATABLE]
      if (navigatable != null && navigatable !is PsiElement) {
        sink[NAVIGATABLE_ARRAY] = arrayOf(navigatable)
      }
    }
    sink.lazyValue(NAVIGATABLE_ARRAY) {
      NavigatableArrayRule.getData(it)
    }
    sink.lazyValue(PROJECT_FILE_DIRECTORY) {
      ProjectFileDirectoryRule.getData(it)
    }
  }
}