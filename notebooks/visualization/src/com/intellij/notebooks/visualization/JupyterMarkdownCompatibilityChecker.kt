// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.lang.Language
import com.intellij.notebooks.jupyter.core.jupyter.CellType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import org.intellij.plugins.markdown.lang.MarkdownCompatibilityChecker

class JupyterMarkdownCompatibilityChecker : MarkdownCompatibilityChecker {
  override fun isSupportedLanguage(language: Language): Boolean = language.id == "Jupyter"
  
  override fun isSupportedContext(language: Language, dataContext: DataContext?): Boolean {
    if (language.id != "Jupyter") return false
    if (dataContext == null) return true
    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return true
    val lineNumber = editor.document.getLineNumber(editor.caretModel.offset)
    val cell = NotebookCellLines.get(editor).getCellByLineNumber(lineNumber) ?: return true
    return cell.type == CellType.MARKDOWN
  }
}