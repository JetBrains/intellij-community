// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.navigation.NavigationItem
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServer
import org.eclipse.lsp4j.WorkspaceSymbol

sealed class LspWorkspaceSymbolCustomizer

/**
 * See LSP specification: [Workspace Symbol](https://microsoft.github.io/language-server-protocol/specification/#workspace_symbol)
 *
 * @see LspSymbolKindCustomizer
 */
open class LspWorkspaceSymbolSupport : LspWorkspaceSymbolCustomizer() {

  /**
   * Creates a NavigationItem for an LSP Workspace Symbol entry that will be displayed in Search Everywhere / Go To Class / Go To Symbol.
   *
   * The default implementation produces an instance of
   * [LspWorkspaceSymbolNavigationItem].
   * Implementors may override this method to customize the item’s presentation, icon, or navigation target
   * (e.g., to return a custom NavigationItem subtype).
   *
   * Important: If you override this method and return a custom NavigationItem, make sure the platform can correctly de-duplicate results.
   * Provide a corresponding [com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider] for your items, or reuse an
   * existing one. See [com.intellij.platform.lsp.impl.features.workspaceSymbol.LspWorkspaceSymbolEqualityProvider] for the default equality
   * implementation used with LSP workspace symbols.
   *
   * @return a NavigationItem to show in Search Everywhere / Go To Class / Go To Symbol, or null to skip this symbol
   */
  open fun createNavigationItem(lspClient: LspClient, workspaceSymbol: WorkspaceSymbol): NavigationItem? {
    @Suppress("DEPRECATION")
    return createNavigationItem(lspClient as LspServer, workspaceSymbol)
  }

  @Deprecated(
    "Override or call createNavigationItem(lspClient, workspaceSymbol) — the LspClient overload",
    ReplaceWith("createNavigationItem(lspServer as LspClient, workspaceSymbol)", "com.intellij.platform.lsp.api.LspClient"),
  )
  @Suppress("DEPRECATION")
  open fun createNavigationItem(lspServer: LspServer, workspaceSymbol: WorkspaceSymbol): NavigationItem? {
    return LspWorkspaceSymbolNavigationItem(lspServer, workspaceSymbol)
  }
}

object LspWorkspaceSymbolDisabled : LspWorkspaceSymbolCustomizer()