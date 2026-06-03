package com.intellij.platform.lsp.impl.features.hierarchy.type

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.features.hierarchy.Lsp4jHierarchyItem
import com.intellij.platform.lsp.impl.features.hierarchy.LspHierarchyNodeDescriptor
import org.eclipse.lsp4j.TypeHierarchySupertypesParams

internal class LspSupertypesHierarchyTreeStructure(
  project: Project,
  baseDescriptor: LspHierarchyNodeDescriptor,
) : LspTypeHierarchyTreeStructure(project, baseDescriptor) {

  override fun fetchItems(nodeDescriptor: LspHierarchyNodeDescriptor): List<Lsp4jHierarchyItem> {
    val typeHierarchyItem = nodeDescriptor.item?.toTypeHierarchyItem()
    if (typeHierarchyItem == null) return emptyList()
    val params = TypeHierarchySupertypesParams(typeHierarchyItem)
    return nodeDescriptor.client.sendRequestSync {
      it.textDocumentService.typeHierarchySupertypes(params)
    }?.map { Lsp4jHierarchyItem.from(it) } ?: emptyList()
  }
}