// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile

sealed class LspTypeHierarchyCustomizer

/**
 * See LSP specification: [Prepare Type Hierarchy](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareTypeHierarchy)
 *
 * @see LspSymbolKindCustomizer
 */
open class LspTypeHierarchySupport : LspTypeHierarchyCustomizer() {

  /**
   * Determines whether type hierarchy functionality should be requested from the LSP server for the given file.
   *
   * @param file the file to check for type hierarchy support
   * @return true if type hierarchy should be requested for this file, false otherwise
   */
  open fun shouldAskServerForTypeHierarchy(file: VirtualFile): Boolean = true
}

object LspTypeHierarchyDisabled : LspTypeHierarchyCustomizer()