// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi.impl

import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

internal val LOG: Logger = Logger.getInstance("#com.intellij.model.psi.impl")

internal fun mockEditor(file: PsiFile): Editor? {
  val project = file.project
  val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
  val editor = object : ImaginaryEditor(project, document) {
    override fun getVirtualFile(): VirtualFile? = file.virtualFile
    override fun toString(): String = "API compatibility editor"
  }
  EditorContextManager.getInstance(file.project).setEditorContext(editor, SingleEditorContext(file.codeInsightContext))
  return editor
}
