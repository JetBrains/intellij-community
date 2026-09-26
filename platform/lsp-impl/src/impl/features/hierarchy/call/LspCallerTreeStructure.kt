package com.intellij.platform.lsp.impl.features.hierarchy.call

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.features.hierarchy.Lsp4jHierarchyItem
import com.intellij.platform.lsp.impl.features.hierarchy.LspHierarchyNodeDescriptor
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams

internal class LspCallerTreeStructure(
  project: Project,
  baseDescriptor: LspHierarchyNodeDescriptor,
) : LspCallHierarchyTreeStructure(project, baseDescriptor) {

  override fun fetchItems(nodeDescriptor: LspHierarchyNodeDescriptor): List<Lsp4jHierarchyItem> {
    val callHierarchyItem = nodeDescriptor.item?.toCallHierarchyItem()
    if (callHierarchyItem == null) return emptyList()
    val params = CallHierarchyIncomingCallsParams(callHierarchyItem)
    return nodeDescriptor.client.sendRequestSync {
      it.textDocumentService.callHierarchyIncomingCalls(params)
    }?.map { Lsp4jHierarchyItem.from(it.from) } ?: emptyList()
  }
}