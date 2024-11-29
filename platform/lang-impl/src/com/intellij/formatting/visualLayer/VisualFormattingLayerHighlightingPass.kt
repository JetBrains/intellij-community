// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal
package com.intellij.formatting.visualLayer

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.formatting.visualLayer.VisualFormattingLayerService.Companion.visualFormattingLayerCodeStyleSettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus.Internal

internal class VisualFormattingLayerHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): EditorBoundHighlightingPass? =
    if (editor.vfmtTimestamp == editor.getCurrentVfmtTimestamp()) null
    else VisualFormattingLayerHighlightingPass(editor, psiFile)

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

}

private class VisualFormattingLayerHighlightingPass(editor: Editor, file: PsiFile) : EditorBoundHighlightingPass(editor, file, true) {

  val service: VisualFormattingLayerService by lazy { VisualFormattingLayerService.getInstance() }

  private var myVisualFormattingLayerElements: List<VisualFormattingLayerElement> = emptyList()

  override fun doCollectInformation(progress: ProgressIndicator) {
    myVisualFormattingLayerElements = service.collectVisualFormattingLayerElements(myEditor)
  }

  override fun doApplyInformationToEditor() {
    service.applyVisualFormattingLayerElementsToEditor(myEditor, myVisualFormattingLayerElements)
    myEditor.vfmtTimestamp = myEditor.getCurrentVfmtTimestamp()
  }

}

private data class Timestamp(val documentStamp: Long, val codeStyleSettingsStamp: Long)

private val VFMT_TIMESTAMP = Key.create<Timestamp>("com.intellij.formatting.visualLayer.VFMT_TIMESTAMP")

private fun Editor.getCurrentVfmtTimestamp(): Timestamp? {
  val vfmtCodeStyle = this.visualFormattingLayerCodeStyleSettings ?: return null
  return Timestamp(
    this.document.modificationStamp,
    vfmtCodeStyle.modificationTracker.modificationCount)
}

private var Editor.vfmtTimestamp
  get() = getUserData(VFMT_TIMESTAMP)
  set(value) = putUserData(VFMT_TIMESTAMP, value)
