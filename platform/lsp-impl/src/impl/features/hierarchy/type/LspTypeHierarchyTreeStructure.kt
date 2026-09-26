package com.intellij.platform.lsp.impl.features.hierarchy.type

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.hierarchy.Lsp4jHierarchyItem
import com.intellij.platform.lsp.impl.features.hierarchy.LspAbstractHierarchyTreeStructure
import com.intellij.platform.lsp.impl.features.hierarchy.LspHierarchyNodeDescriptor
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TypeHierarchyPrepareParams

internal abstract class LspTypeHierarchyTreeStructure(
  project: Project,
  baseDescriptor: LspHierarchyNodeDescriptor,
) : LspAbstractHierarchyTreeStructure(project, baseDescriptor) {

  override fun fetchRootItem(client: LspClientImpl, textDocument: TextDocumentIdentifier, position: Position): Lsp4jHierarchyItem? {
    val typeHierarchyItem = client.sendRequestSync {
      it.textDocumentService.prepareTypeHierarchy(
        TypeHierarchyPrepareParams(textDocument, position)
      )
    }?.firstOrNull() ?: return null

    return Lsp4jHierarchyItem.from(typeHierarchyItem)
  }
}