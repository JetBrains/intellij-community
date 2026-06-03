package com.intellij.platform.lsp.impl.features.hierarchy.type

import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.ide.hierarchy.ViewSubtypesHierarchyAction
import com.intellij.ide.hierarchy.ViewSupertypesHierarchyAction
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.hierarchy.LspFakePsiElement
import com.intellij.platform.lsp.impl.features.hierarchy.LspHierarchyNodeDescriptor
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import javax.swing.JPanel
import javax.swing.JTree

internal class LspTypeHierarchyBrowser(project: Project, element: PsiElement, private val client: LspClientImpl) :
  TypeHierarchyBrowserBase(project, element) {

  override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
    if (descriptor is LspHierarchyNodeDescriptor) {
      return descriptor.psiElement
    }
    return null
  }

  override fun prependActions(actionGroup: DefaultActionGroup) {
    actionGroup.add(ViewSupertypesHierarchyAction())
    actionGroup.add(ViewSubtypesHierarchyAction())
    actionGroup.add(AlphaSortAction())
  }

  override fun createTrees(trees: MutableMap<in @Nls String, in JTree>) {
    val baseOnThisTypeAction = createBaseOnThisAction()
    val supertypesTree = createTree(true)
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).shortcutSet, supertypesTree)
    supertypesTree.isRootVisible = false
    trees[getSupertypesHierarchyType()] = supertypesTree

    val subtypesTree = createTree(true)
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).shortcutSet, subtypesTree)
    subtypesTree.isRootVisible = false
    trees[getSubtypesHierarchyType()] = subtypesTree
  }

  override fun isApplicableElement(element: PsiElement): Boolean {
    return element is LspFakePsiElement
  }

  override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? {
    return when (type) {
      getSupertypesHierarchyType() ->
        LspSupertypesHierarchyTreeStructure(myProject, getBaseDescriptor(psiElement))
      getSubtypesHierarchyType() ->
        LspSubtypesHierarchyTreeStructure(myProject, getBaseDescriptor(psiElement))
      getTypeHierarchyType() -> null
      else -> error("unexpected type: $type")
    }
  }

  override fun getComparator(): Comparator<NodeDescriptor<*>?>? {
    val state = HierarchyBrowserManager.getInstance(myProject).state
    return if (state != null && state.SORT_ALPHABETICALLY) AlphaComparator.getInstance() else compareBy { it?.index }
  }

  override fun isInterface(psiElement: PsiElement): Boolean = false

  override fun canBeDeleted(psiElement: PsiElement?): Boolean = false

  override fun getQualifiedName(psiElement: PsiElement?): String? = null

  override fun createLegendPanel(): JPanel? = null

  private fun getBaseDescriptor(psiElement: PsiElement): LspHierarchyNodeDescriptor {
    return LspHierarchyNodeDescriptor(client, null, psiElement, null, true)
  }
}