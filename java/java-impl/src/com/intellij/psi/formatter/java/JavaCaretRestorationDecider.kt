// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.formatting.CaretRestorationDecider
import com.intellij.formatting.DefaultCaretRestorationDecider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiLiteralExpression

public class JavaCaretRestorationDecider : CaretRestorationDecider {
  override fun shouldRestoreCaret(document: Document, editor: Editor, caretOffset: Int): Boolean {
    val defaultRestorationResult = DefaultCaretRestorationDecider.shouldRestoreCaret(document, editor, caretOffset)
    
    val project = editor.project ?: return defaultRestorationResult
    val manager = PsiDocumentManager.getInstance(project) ?: return defaultRestorationResult
    val file = manager.getPsiFile(document) ?: return defaultRestorationResult

    val element = file.findElementAt(caretOffset) ?: return defaultRestorationResult

    if (element.parent is PsiLiteralExpression) {
      return false
    }
    return defaultRestorationResult
  }
}