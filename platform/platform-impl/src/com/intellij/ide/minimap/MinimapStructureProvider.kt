// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager

class MinimapStructureProvider(private val project: Project?, private val parentDisposable: Disposable) {
  fun createModel(editor: Editor): StructureViewModel? {
    project ?: return null

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
    val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile)
    if (builder !is TreeBasedStructureViewBuilder) return null

    val model = builder.createStructureViewModel(editor).apply {
      Disposer.register(parentDisposable, this)
    }

    return model
  }
}