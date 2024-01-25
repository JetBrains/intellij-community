// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiFile

interface QuickDocSyntaxHighlightingHandlerFactory {
  companion object {
    internal val EXTENSION: LanguageExtension<QuickDocSyntaxHighlightingHandlerFactory> =
      LanguageExtension("com.intellij.lang.documentation.syntaxHighlightingHandlerFactory")
  }

  fun createHandler(): QuickDocSyntaxHighlightingHandler
}

/**
 * Provides interface for special support for a language syntax highlighting.
 * Can store state, especially useful between calls to [preprocessCode] and [postProcessHtml].
 */
interface QuickDocSyntaxHighlightingHandler {

  /**
   * Modify the code before sending it for syntax highlighting.
   * E.g. PHP code requires `<?php` prefix
   */
  fun preprocessCode(code: String): String =
    code

  /**
   * Remove any modifications made in [preprocessCode].
   */
  fun postProcessHtml(html: String): String =
    html

  /**
   * Allows to perform additional, semantic syntax highlighting,
   * like semantic keywords or method calls.
   */
  fun performSemanticHighlighting(file: PsiFile): List<HighlightInfo> =
    emptyList()

  interface HighlightInfo {

    val startOffset: Int

    val endOffset: Int

    fun getTextAttributes(scheme: EditorColorsScheme): TextAttributes?

  }
}