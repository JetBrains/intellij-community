// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.codeInsight.actions.ReaderModeProvider
import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.codeInsight.daemon.impl.JavaLensProvider
import com.intellij.codeInsight.hints.HintUtils
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.JavaInlayHintsProvider
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

class JavaInlaysReaderModeProvider : ReaderModeProvider {
  override fun applyModeChanged(project: Project, editor: Editor, readerMode: Boolean, fileIsOpenAlready: Boolean) {
    val showHints = readerMode && ReaderModeSettings.instance(project).showInlaysHints
    val key = HintUtils.getHintProvidersForLanguage(JavaLanguage.INSTANCE, project).map { it.provider }.find { it is JavaLensProvider }?.key
    InlayHintsPassFactory.setAlwaysEnabledHintsProviders(editor, if (showHints && key != null) { listOf(key) } else { null })
  }
}