package com.intellij.notebooks.visualization

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

interface NotebookCellSelectionModelProvider {
  fun create(editor: Editor): NotebookCellSelectionModel

  companion object : LanguageExtension<NotebookCellSelectionModelProvider>(ID)
}

val Editor.cellSelectionModel: NotebookCellSelectionModel?
  get() {
    return key.get(this) ?: install(this)
  }

val Editor.hasCellSelectionModelSupport: Boolean
  get() = key.get(this) != null || getProvider(this) != null

private val key = Key.create<NotebookCellSelectionModel>(NotebookCellSelectionModel::class.java.name)

private const val ID: String = "org.jetbrains.plugins.notebooks.notebookCellSelectionModelProvider"

private fun install(editor: Editor): NotebookCellSelectionModel? {
  val model = getProvider(editor)?.create(editor)
  key.set(editor, model)
  return model
}

private fun getProvider(editor: Editor): NotebookCellSelectionModelProvider? {
  val project = editor.project ?: return null
  val language = getLanguage(project, editor.document) ?: return null
  return NotebookCellSelectionModelProvider.forLanguage(language)
}