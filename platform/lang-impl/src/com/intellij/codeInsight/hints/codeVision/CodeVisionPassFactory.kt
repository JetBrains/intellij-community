// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile

private val isCodeVisionEnabled: Boolean
  get() = Registry.`is`("editor.codeVision.new", true)

private val PSI_MODIFICATION_STAMP = Key.create<Long>("code.vision.psi.modification.stamp")

internal class CodeVisionPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (!isCodeVisionEnabled) {
      return null
    }

    val savedStamp = editor.getUserData(PSI_MODIFICATION_STAMP)
    val currentStamp = getCurrentModificationStamp(psiFile)
    if (savedStamp != null && savedStamp == currentStamp) return null

    return CodeVisionPass(psiFile, editor)
  }
}

object ModificationStampUtil {
  fun putCurrentModificationStamp(editor: Editor, file: PsiFile) {
    editor.putUserData(PSI_MODIFICATION_STAMP, getCurrentModificationStamp(file))
  }

  fun clearModificationStamp(editor: Editor) {
    editor.putUserData(PSI_MODIFICATION_STAMP, null)
  }

  fun getModificationStamp(editor: Editor): Long? {
    return editor.getUserData(PSI_MODIFICATION_STAMP)
  }

  internal fun clearModificationStamp() {
    for (editor in EditorFactory.getInstance().allEditors) {
      clearModificationStamp(editor)
    }
  }
}


private fun getCurrentModificationStamp(file: PsiFile): Long {
  return file.manager.modificationTracker.modificationCount
}
