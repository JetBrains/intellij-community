// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.documentSymbol

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.impl.features.navigation.navigateToLspPosition
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class LspStructureViewSupport private constructor(
  private val lspServer: LspServerImpl,
  private val file: VirtualFile,
) {
  fun getDocumentSymbols(): List<DocumentSymbol> = lspServer.requestExecutor.getDocumentSymbolsCaching(file).orEmpty()

  fun getIcon(symbol: DocumentSymbol): Icon? = lspServer.descriptor.lspCustomization.symbolKindCustomizer.getIcon(symbol.kind)

  fun navigate(position: Position, requestFocus: Boolean) {
    navigateToLspPosition(file, lspServer.project, position, requestFocus)
  }

  companion object {
    @JvmStatic
    fun find(project: Project, file: VirtualFile): LspStructureViewSupport? {
      return LspServerManagerImpl.getInstanceImpl(project).getServersWithThisFileOpen(file).firstOrNull {
        it.descriptor.lspCustomization.documentSymbolCustomizer.structureViewSupport &&
        it.supportsDocumentSymbol(file)
      }?.let { LspStructureViewSupport(it, file) }
    }
  }
}
