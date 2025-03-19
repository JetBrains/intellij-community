// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.jupyter.core.lang

import com.intellij.lang.Language

/**
 * General language for notebooks.
 * To share some of the common features for the notebooks, their language should use [NotebookLanguage] as base one.
 */
object NotebookLanguage: Language("Notebook")
