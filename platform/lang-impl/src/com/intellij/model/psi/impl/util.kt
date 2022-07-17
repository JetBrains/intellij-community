// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

internal val LOG: Logger = Logger.getInstance("#com.intellij.model.psi.impl")

internal fun mockEditor(file: PsiFile): Editor? {
  val project = file.project
  val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
  return object : ImaginaryEditor(project, document) {
    override fun toString(): String = "API compatibility editor"
  }
}
