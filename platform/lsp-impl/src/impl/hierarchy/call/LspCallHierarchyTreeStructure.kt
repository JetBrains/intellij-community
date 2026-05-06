package com.intellij.platform.lsp.impl.hierarchy.call

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.hierarchy.Lsp4jHierarchyItem
import com.intellij.platform.lsp.impl.hierarchy.LspAbstractHierarchyTreeStructure
import com.intellij.platform.lsp.impl.hierarchy.LspHierarchyNodeDescriptor
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

internal abstract class LspCallHierarchyTreeStructure(
  project: Project,
  baseDescriptor: LspHierarchyNodeDescriptor,
) : LspAbstractHierarchyTreeStructure(project, baseDescriptor) {

  override fun fetchRootItem(server: LspServerImpl, textDocument: TextDocumentIdentifier, position: Position): Lsp4jHierarchyItem? {
    val callHierarchyItem = server.sendRequestSync {
      it.textDocumentService.prepareCallHierarchy(
        CallHierarchyPrepareParams(textDocument, position)
      )
    }?.firstOrNull() ?: return null

    return Lsp4jHierarchyItem.from(callHierarchyItem)
  }
}