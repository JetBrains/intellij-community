// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

sealed class LspDocumentSymbolCustomizer {
  abstract val structureViewSupport: Boolean
  abstract val breadcrumbsSupport: Boolean
}

/**
 * Handles [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentSymbol) requests.
 *
 * @see LspSymbolKindCustomizer
 */
open class LspDocumentSymbolSupport : LspDocumentSymbolCustomizer() {
  /**
   * Controls the Structure tool window & File Structure popup.
   */
  override val structureViewSupport: Boolean = true

  /**
   * Controls breadcrumbs, but also sticky lines, diff viewer headers and items in the Recent Locations popup.
   */
  override val breadcrumbsSupport: Boolean = true
}

object LspDocumentSymbolDisabled : LspDocumentSymbolCustomizer() {
  override val structureViewSupport: Boolean = false
  override val breadcrumbsSupport: Boolean = false
}