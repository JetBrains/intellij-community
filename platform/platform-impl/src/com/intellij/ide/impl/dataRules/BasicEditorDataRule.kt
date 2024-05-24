// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.EdtDataRule
import com.intellij.openapi.actionSystem.PlatformDataKeys.*
import com.intellij.openapi.editor.ex.EditorEx

internal class BasicEditorDataRule : EdtDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val editor = snapshot[EDITOR]
    if (editor !is EditorEx) return

    sink[COPY_PROVIDER] = editor.getCopyProvider()
    sink[PASTE_PROVIDER] = editor.getPasteProvider()
    sink[CUT_PROVIDER] = editor.getCutProvider()
    sink[DELETE_ELEMENT_PROVIDER] = editor.getDeleteProvider()
  }
}