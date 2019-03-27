// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement

class InlayHintsPass(
  val rootElement: PsiElement,
  val collectors: List<CollectorWithSettings<out Any>>,
  editor: Editor,
  val settings: InlayHintsSettings
) : EditorBoundHighlightingPass(editor, rootElement.containingFile, true) {
  override fun doCollectInformation(progress: ProgressIndicator) {
    for (collector in collectors) {
      val enabled = settings.hintsEnabled(collector.key, collector.language)
      collector.collectHints(myFile, enabled, myEditor)
    }
  }

  override fun doApplyInformationToEditor() {
    val wrapper = InlayModelWrapper(myEditor.inlayModel)
    for (collector in collectors) {
      collector.applyToEditor(myFile, myEditor, wrapper)
    }
  }
}
