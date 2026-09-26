package com.intellij.platform.lsp.impl.features.hierarchy.call

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.hierarchy.Lsp4jHierarchyItem
import com.intellij.platform.lsp.impl.features.hierarchy.LspAbstractHierarchyTreeStructure
import com.intellij.platform.lsp.impl.features.hierarchy.LspHierarchyNodeDescriptor
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

internal abstract class LspCallHierarchyTreeStructure(
  project: Project,
  baseDescriptor: LspHierarchyNodeDescriptor,
) : LspAbstractHierarchyTreeStructure(project, baseDescriptor) {

  override fun fetchRootItem(client: LspClientImpl, textDocument: TextDocumentIdentifier, position: Position): Lsp4jHierarchyItem? {
    val callHierarchyItem = client.sendRequestSync {
      it.textDocumentService.prepareCallHierarchy(
        CallHierarchyPrepareParams(textDocument, position)
      )
    }?.firstOrNull() ?: return null

    return Lsp4jHierarchyItem.from(callHierarchyItem)
  }
}