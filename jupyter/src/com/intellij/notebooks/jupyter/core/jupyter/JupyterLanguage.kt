// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notebooks.jupyter.core.jupyter

import com.intellij.lang.Language
import com.intellij.notebooks.jupyter.core.lang.NotebookLanguage
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType

object JupyterLanguage : Language(NotebookLanguage, "Jupyter")

val FileType.isJupyterLanguage: Boolean
  get() = (this as? LanguageFileType)?.language is JupyterLanguage