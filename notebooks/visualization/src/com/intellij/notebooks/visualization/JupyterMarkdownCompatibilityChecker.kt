// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.lang.Language
import org.intellij.plugins.markdown.lang.MarkdownCompatibilityChecker

class JupyterMarkdownCompatibilityChecker : MarkdownCompatibilityChecker {
  override fun isSupportedLanguage(language: Language): Boolean = language.id == "Jupyter"
}