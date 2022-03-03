// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.codeInsight.actions.ReaderModeProvider
import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.codeInsight.daemon.impl.JavaCodeVisionProvider
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

private class JavaInlaysReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean, fileIsOpenAlready: Boolean) {
    InlayHintsPassFactory.setAlwaysEnabledHintsProviders(
      editor = editor,
      keys = if (readerMode && ReaderModeSettings.getInstance(project).showInlaysHints) {
        listOf(JavaCodeVisionProvider.getSettingsKey())
      }
      else {
        null
      }
    )
  }
}