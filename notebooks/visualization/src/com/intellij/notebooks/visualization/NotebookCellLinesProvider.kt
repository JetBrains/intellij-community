package com.intellij.notebooks.visualization

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.TestOnly

private const val ID: String = "org.jetbrains.plugins.notebooks.notebookCellLinesProvider"

interface NotebookCellLinesProvider : IntervalsGenerator {
  fun create(document: Document): NotebookCellLines

  companion object : LanguageExtension<NotebookCellLinesProvider>(ID) {
    private val key = Key.create<NotebookCellLinesProvider>(NotebookCellLinesProvider::class.java.name)

    fun install(editor: Editor): NotebookCellLinesProvider? {
      get(editor.document)?.let { return it }
      val project = editor.project ?: return null
      return install(project, editor.document)
    }

    fun install(project: Project, document: Document): NotebookCellLinesProvider? {
      get(document)?.let { return it }
      val language = getLanguage(project, document) ?: return null
      val provider = forLanguage(language) ?: return null
      key.set(document, provider)
      return provider
    }

    fun get(document: Document): NotebookCellLinesProvider? {
      return document.getUserData(key)
    }

    fun install(document: Document, provider: NotebookCellLinesProvider): NotebookCellLines {
      key.set(document, provider)
      return provider.create(document)
    }
  }
}

internal fun getLanguage(project: Project, document: Document): Language? =
  PsiDocumentManager.getInstance(project).getPsiFile(document)?.language