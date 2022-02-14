package org.jetbrains.plugins.notebooks.core.api.lang

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType

/**
 * General language for notebooks.
 * To share some of the common features for the notebooks, their language should use [NotebookLanguage] as base one.
 */
object NotebookLanguage: Language("Notebook")

val FileType.isNotebookLanguage: Boolean
  get() = (this as? LanguageFileType)?.language?.baseLanguage is NotebookLanguage