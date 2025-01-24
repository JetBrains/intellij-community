// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.jupyter.core.jupyter

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor

/**
 * Velocity file provider breaks on .ipynb.ft files
 * in case there is no such substitutor
 */
class JupyterTemplateLanguageSubstitutor : LanguageSubstitutor() {
  override fun getLanguage(file: VirtualFile, project: Project): Language? {
    if (file.extension == "ft") {
      return PlainTextLanguage.INSTANCE
    }
    return null
  }
}
