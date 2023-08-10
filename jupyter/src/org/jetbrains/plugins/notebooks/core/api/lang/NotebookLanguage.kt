package org.jetbrains.plugins.notebooks.core.api.lang

import com.intellij.lang.Language

/**
 * General language for notebooks.
 * To share some of the common features for the notebooks, their language should use [NotebookLanguage] as base one.
 */
object NotebookLanguage: Language("Notebook")