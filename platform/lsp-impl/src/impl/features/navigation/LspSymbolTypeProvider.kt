package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.codeInsight.navigation.SymbolTypeProvider
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction
import com.intellij.model.Symbol
import com.intellij.openapi.project.Project

/**
 * Used for [Go To Type Declaration][GotoTypeDeclarationAction] feature backed by the information from an LSP server
 * ([textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_typeDefinition) request).
 *
 * This class relies on the result calculated in [LspImplicitReferenceProvider.getImplicitReference].
 */
internal class LspSymbolTypeProvider : SymbolTypeProvider {
  // When [GotoTypeDeclarationAction] action is running,
  // the `LspNavigatableSymbol` is a result of `LspResolvedSymbolReference.resolveReference`,
  // and it points to the type declaration as a result of the `textDocument/typeDefinition` request
  override fun getSymbolTypes(project: Project, symbol: Symbol): List<Symbol> =
    (symbol as? LspNavigatableSymbol)?.let { listOf(it) }
    ?: emptyList()
}
