package org.jetbrains.plugins.notebooks.visualization

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager

private const val ID: String = "org.jetbrains.plugins.notebooks.notebookCellLinesProvider"

interface NotebookCellLinesProvider {
  fun create(document: Document): NotebookCellLines

  companion object : LanguageExtension<NotebookCellLinesProvider>(ID)
}

internal fun getLanguage(editor: Editor): Language? =
  editor
    .project
    ?.let(PsiDocumentManager::getInstance)
    ?.getPsiFile(editor.document)
    ?.language

internal val Editor.notebookCellLinesProvider: NotebookCellLinesProvider?
  get() = getLanguage(this)
    ?.let(NotebookCellLinesProvider::forLanguage)