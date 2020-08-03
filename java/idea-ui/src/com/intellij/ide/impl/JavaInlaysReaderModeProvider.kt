// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.codeInsight.actions.ReaderModeProvider
import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

class JavaInlaysReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean, fileIsOpenAlready: Boolean) {
    InlayHintsPassFactory.setHintsEnabled(editor, readerMode && ReaderModeSettings.instance(project).showInlaysHints)
  }
}