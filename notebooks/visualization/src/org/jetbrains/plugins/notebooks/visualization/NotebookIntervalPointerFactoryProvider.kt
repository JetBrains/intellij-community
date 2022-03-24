package org.jetbrains.plugins.notebooks.visualization

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Editor

private const val ID: String = "org.jetbrains.plugins.notebooks.notebookIntervalPointerFactoryProvider"

interface NotebookIntervalPointerFactoryProvider {
  fun create(editor: Editor): NotebookIntervalPointerFactory

  companion object : LanguageExtension<NotebookIntervalPointerFactoryProvider>(ID)
}