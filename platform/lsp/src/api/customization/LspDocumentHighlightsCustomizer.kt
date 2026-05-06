// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.psi.PsiFile

sealed class LspDocumentHighlightsCustomizer

/**
 * Base class for customizing LSP highlight usage behavior in language plugins.
 * See LSP specification: [Document Highlights](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentHighlight)
 */
open class LspDocumentHighlightsSupport : LspDocumentHighlightsCustomizer() {
  /**
   * Determines whether document highlights should be processed for the given file.
   */
  open fun shouldAskServerForDocumentHighlights(psiFile: PsiFile): Boolean {
    // Plain Text files and files with TextMate bundle-based basic syntax highlighting coming from TextMate bundles
    // do not have PSI and advanced language support. So LSP-based document highlighting (highlighting usages of the symbol under the caret)
    // definitely makes sense for such files. Other files are very likely to have PSI and first-class support with
    // built-in identifier highlighting, so they probably don't need LSP-based document highlights.
    // The plugins are free to override this logic.

    return psiFile.language.id.let { it == "TEXT" || it == "textmate" }
  }
}

object LspDocumentHighlightsDisabled : LspDocumentHighlightsCustomizer()