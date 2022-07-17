// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.lensContextOrThrow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile

class CodeVisionPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (registry.value.asBoolean().not()) return null

    return CodeVisionPass(file, editor)
  }

  companion object {
    private val registry = lazy { Registry.get("editor.codeVision.new") }

    @JvmStatic
    fun applyPlaceholders(editor: Editor, placeholders: MutableList<Pair<TextRange, CodeVisionEntry>>) {
      if (registry.value.asBoolean().not()) return
      editor.lensContextOrThrow.setResults(placeholders)
    }
  }
}