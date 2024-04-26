package com.intellij.jupyter.core.lang

import com.intellij.lang.Language

/**
 * General language for notebooks.
 * To share some of the common features for the notebooks, their language should use [NotebookLanguage] as base one.
 */
object NotebookLanguage: Language("Notebook")