// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile


class VisualFormattingLayerHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  override fun createHighlightingPass(file: PsiFile, editor: Editor) = VisualFormattingLayerHighlightingPass(editor, file)
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }
}

class VisualFormattingLayerHighlightingPass(editor: Editor, file: PsiFile) : EditorBoundHighlightingPass(editor, file, true) {

  val service: VisualFormattingLayerService by lazy { VisualFormattingLayerService.getInstance() }

  var myVisualFormattingLayerElements: List<VisualFormattingLayerElement> = emptyList()

  override fun doCollectInformation(progress: ProgressIndicator) {
    //progress.start()

    myVisualFormattingLayerElements = service.collectVisualFormattingLayerElements(myEditor)

    //progress.stop()
  }

  override fun doApplyInformationToEditor() {
    service.applyVisualFormattingLayerElementsToEditor(myEditor, myVisualFormattingLayerElements)
  }

}
