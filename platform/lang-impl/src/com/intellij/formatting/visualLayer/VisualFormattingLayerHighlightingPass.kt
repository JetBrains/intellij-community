// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile


class VisualFormattingLayerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar) : DirtyScopeTrackingHighlightingPassFactory {
  class Registrar : TextEditorHighlightingPassFactoryRegistrar {
    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
      VisualFormattingLayerHighlightingPassFactory(registrar)
    }
  }

  private val passId = registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)

  override fun getPassId(): Int = passId

  override fun createHighlightingPass(file: PsiFile, editor: Editor): VisualFormattingLayerHighlightingPass? {
    FileStatusMap.getDirtyTextRange(editor.document, file, passId) ?: return null
    return VisualFormattingLayerHighlightingPass(editor, file)
  }

}

class VisualFormattingLayerHighlightingPass(editor: Editor, file: PsiFile) : EditorBoundHighlightingPass(editor, file, true) {

  val service: VisualFormattingLayerService by lazy { VisualFormattingLayerService.getInstance() }

  private var myVisualFormattingLayerElements: List<VisualFormattingLayerElement> = emptyList()

  override fun doCollectInformation(progress: ProgressIndicator) {
    myVisualFormattingLayerElements = service.collectVisualFormattingLayerElements(myEditor)
  }

  override fun doApplyInformationToEditor() {
    service.applyVisualFormattingLayerElementsToEditor(myEditor, myVisualFormattingLayerElements)
  }

}
