// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jupyter.core.jupyter

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.jupyter.core.lang.NotebookLanguage

object JupyterLanguage : Language(NotebookLanguage, "Jupyter")

val FileType.isJupyterLanguage: Boolean
  get() = (this as? LanguageFileType)?.language is JupyterLanguage