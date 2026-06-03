package com.intellij.platform.lsp.impl.features.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

internal abstract class LspAbstractHierarchyTreeStructure(
  project: Project,
  baseDescriptor: LspHierarchyNodeDescriptor,
) : HierarchyTreeStructure(project, baseDescriptor) {

  override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<LspHierarchyNodeDescriptor> {
    val node = descriptor as? LspHierarchyNodeDescriptor ?: return emptyArray()

    if (node.isBase) {
      return buildVisibleRoot(node)
    }

    return fetchItems(node)
      .mapNotNull { item -> createNodeDescriptorForItem(item, node) }
      .toTypedArray()
  }

  protected abstract fun fetchItems(nodeDescriptor: LspHierarchyNodeDescriptor): List<Lsp4jHierarchyItem>
  protected abstract fun fetchRootItem(client: LspClientImpl, textDocument: TextDocumentIdentifier, position: Position): Lsp4jHierarchyItem?

  private fun prepareRootItem(rootDescriptor: LspHierarchyNodeDescriptor): Lsp4jHierarchyItem? {
    val psiFile = rootDescriptor.containingFile ?: return null
    val file = psiFile.virtualFile ?: return null

    val hostPosition = (rootDescriptor.psiElement as? LspFakePsiElement)?.lsp4jPosition ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val offset = getOffsetInDocument(document, hostPosition) ?: return null
    val docPosition = rootDescriptor.client.documentMapping.getDocumentPosition(file, document, offset) ?: return null

    return fetchRootItem(rootDescriptor.client, docPosition.document.id, docPosition.position)
  }

  private fun createNodeDescriptorForItem(
    item: Lsp4jHierarchyItem,
    parentDescriptor: LspHierarchyNodeDescriptor,
  ): LspHierarchyNodeDescriptor? {
    val file = getVirtualFileForItem(item, parentDescriptor) ?: return null
    val psiFile = PsiManager.getInstance(myProject).findFile(file) ?: return null
    return createDescriptor(item, parentDescriptor, psiFile)
  }

  private fun createDescriptor(item: Lsp4jHierarchyItem, parent: LspHierarchyNodeDescriptor, psiFile: PsiFile): LspHierarchyNodeDescriptor {
    return LspHierarchyNodeDescriptor(
      client = parent.client,
      item = item,
      element = LspFakePsiElement(psiFile = psiFile, name = item.name, lsp4jPosition = item.selectionRange.start),
      parent = parent,
      isBase = false,
    )
  }

  private fun buildVisibleRoot(rootDescriptor: LspHierarchyNodeDescriptor): Array<LspHierarchyNodeDescriptor> {
    val psiFile = rootDescriptor.containingFile ?: return emptyArray()
    val item = prepareRootItem(rootDescriptor) ?: return emptyArray()
    val child = createDescriptor(item, rootDescriptor, psiFile)
    return arrayOf(child)
  }

  private fun getVirtualFileForItem(item: Lsp4jHierarchyItem, descriptor: LspHierarchyNodeDescriptor): VirtualFile? {
    val uri = item.uri
    return descriptor.client.descriptor.findFileByUri(uri)
  }
}
