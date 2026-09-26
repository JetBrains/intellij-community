package com.intellij.platform.lsp.impl.features.workspaceSymbol

import org.eclipse.lsp4j.SymbolKind

internal class LspGoToClassContributor : LspWorkspaceSymbolContributor() {
  override fun shouldAcceptSymbolKind(symbolKind: SymbolKind): Boolean {
    when (symbolKind) {
      SymbolKind.Class, SymbolKind.Interface, SymbolKind.Enum, SymbolKind.Struct -> return true
      else -> return false
    }
  }
}