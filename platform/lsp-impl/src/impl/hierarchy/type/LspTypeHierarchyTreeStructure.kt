package com.intellij.platform.lsp.impl.hierarchy.type

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.hierarchy.Lsp4jHierarchyItem
import com.intellij.platform.lsp.impl.hierarchy.LspAbstractHierarchyTreeStructure
import com.intellij.platform.lsp.impl.hierarchy.LspHierarchyNodeDescriptor
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TypeHierarchyPrepareParams

internal abstract class LspTypeHierarchyTreeStructure(
  project: Project,
  baseDescriptor: LspHierarchyNodeDescriptor,
) : LspAbstractHierarchyTreeStructure(project, baseDescriptor) {

  override fun fetchRootItem(server: LspServerImpl, textDocument: TextDocumentIdentifier, position: Position): Lsp4jHierarchyItem? {
    val typeHierarchyItem = server.sendRequestSync {
      it.textDocumentService.prepareTypeHierarchy(
        TypeHierarchyPrepareParams(textDocument, position)
      )
    }?.firstOrNull() ?: return null

    return Lsp4jHierarchyItem.from(typeHierarchyItem)
  }
}