package com.intellij.platform.lsp.impl.features.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.hierarchy.LspFakePsiElement
import com.intellij.platform.lsp.impl.features.hierarchy.LspHierarchyNodeDescriptor
import com.intellij.psi.PsiElement
import com.intellij.ui.tree.ui.DefaultTreeUI
import org.jetbrains.annotations.Nls
import java.util.function.Function
import javax.swing.JTree
import javax.swing.tree.TreeNode

internal class LspCallHierarchyBrowser(project: Project, element: PsiElement, private val client: LspClientImpl) :
  CallHierarchyBrowserBase(project, element) {
  override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
    if (descriptor is LspHierarchyNodeDescriptor) {
      return descriptor.psiElement
    }
    return null
  }

  override fun prependActions(actionGroup: DefaultActionGroup) {
    super.prependActions(actionGroup)
    actionGroup.childActionsOrStubs
      .filterIsInstance<ChangeScopeAction>()
      .forEach { actionGroup.remove(it) }
  }

  override fun createTrees(trees: MutableMap<in @Nls String, in JTree>) {
    trees[getCallerType()] = createHierarchyTree()
    trees[getCalleeType()] = createHierarchyTree()
  }

  override fun isApplicableElement(element: PsiElement): Boolean {
    return element is LspFakePsiElement
  }

  override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? {
    return when (type) {
      getCallerType() -> LspCallerTreeStructure(myProject, getBaseDescriptor(psiElement))
      getCalleeType() -> LspCalleeTreeStructure(myProject, getBaseDescriptor(psiElement))
      else -> error("unexpected type: $type")
    }
  }

  override fun getComparator(): Comparator<NodeDescriptor<*>?>? {
    val state = HierarchyBrowserManager.getInstance(myProject).state
    return if (state != null && state.SORT_ALPHABETICALLY) AlphaComparator.getInstance() else compareBy { it?.index }
  }

  private fun createHierarchyTree(): JTree {
    val tree = createTree(false)
    tree.setRootVisible(false)
    tree.putClientProperty(
      DefaultTreeUI.AUTO_EXPAND_FILTER,
      Function(::skipAutoExpand)
    )
    return tree
  }

  private fun skipAutoExpand(node: Any): Boolean {
    return (node as? TreeNode)?.parent?.parent != null
  }

  private fun getBaseDescriptor(psiElement: PsiElement): LspHierarchyNodeDescriptor {
    return LspHierarchyNodeDescriptor(client, null, psiElement, null, true)
  }
}