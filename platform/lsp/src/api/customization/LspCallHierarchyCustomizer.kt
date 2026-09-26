// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile

sealed class LspCallHierarchyCustomizer

/**
 * See LSP specification: [Prepare Call Hierarchy](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareCallHierarchy)
 *
 * @see LspSymbolKindCustomizer
 */
open class LspCallHierarchySupport : LspCallHierarchyCustomizer() {

  /**
   * Determines whether call hierarchy functionality should be requested from the LSP server for the given file.
   *
   * @param file the file to check for call hierarchy support
   * @return true if call hierarchy should be requested for this file, false otherwise
   */
  open fun shouldAskServerForCallHierarchy(file: VirtualFile): Boolean = true
}

object LspCallHierarchyDisabled : LspCallHierarchyCustomizer()