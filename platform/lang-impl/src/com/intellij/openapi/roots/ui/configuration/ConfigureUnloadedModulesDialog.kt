/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.ModuleGroupingImplementation
import com.intellij.ide.projectView.impl.ModuleGroupingTreeHelper
import com.intellij.openapi.module.*
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.tree.TreeUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.*
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreePath
import kotlin.comparisons.compareBy

/**
 * @author nik
 */
class ConfigureUnloadedModulesDialog(private val project: Project, selectedModules: Array<Module>?) : DialogWrapper(project) {
  private val loadedModulesTree = ModuleDescriptionsTree(project)
  private val unloadedModulesTree = ModuleDescriptionsTree(project)
  private val moduleDescriptions = ModuleManager.getInstance(project).allModuleDescriptions.associateBy { it.name }
  private val statusLabel = JBLabel()

  init {
    title = ProjectBundle.message("module.load.unload.dialog.title")
    loadedModulesTree.fillTree(moduleDescriptions.values.filter { it is LoadedModuleDescriptionImpl })
    loadedModulesTree.selectNodes(selectedModules?.mapTo(HashSet<String>()) { it.name } ?: emptySet<String>())
    unloadedModulesTree.fillTree(moduleDescriptions.values.filter { it is UnloadedModuleDescription })
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val buttonsPanel = JPanel(VerticalFlowLayout())
    val moveToUnloadedButton = JButton(ProjectBundle.message("module.unload.button.text"))
    val moveToLoadedButton = JButton(ProjectBundle.message("module.load.button.text"))
    val moveAllToUnloadedButton = JButton(ProjectBundle.message("module.unload.all.button.text"))
    val moveAllToLoadedButton = JButton(ProjectBundle.message("module.load.all.button.text"))
    moveToUnloadedButton.addActionListener {
      moveSelectedNodes(loadedModulesTree, unloadedModulesTree)
    }
    moveToLoadedButton.addActionListener {
      moveSelectedNodes(unloadedModulesTree, loadedModulesTree)
    }
    moveAllToUnloadedButton.addActionListener {
      moveAllNodes(loadedModulesTree, unloadedModulesTree)
    }
    moveAllToLoadedButton.addActionListener {
      moveAllNodes(unloadedModulesTree, loadedModulesTree)
    }
    buttonsPanel.add(moveToUnloadedButton)
    buttonsPanel.add(moveToLoadedButton)
    buttonsPanel.add(moveAllToUnloadedButton)
    buttonsPanel.add(moveAllToLoadedButton)

    val gridBag = GridBag().setDefaultWeightX(0, 1.0).setDefaultWeightX(1, 0.0).setDefaultWeightX(2, 1.0)
    val mainPanel = JPanel(GridBagLayout())
    mainPanel.add(JBLabel(ProjectBundle.message("module.loaded.label.text")), gridBag.nextLine().next().anchor(GridBagConstraints.WEST))
    mainPanel.add(JBLabel(ProjectBundle.message("module.unloaded.label.text")), gridBag.next().next().anchor(GridBagConstraints.WEST))

    mainPanel.add(JBScrollPane(loadedModulesTree.tree), gridBag.nextLine().next().weighty(1.0).fillCell())
    mainPanel.add(buttonsPanel, gridBag.next().anchor(GridBagConstraints.CENTER))
    mainPanel.add(JBScrollPane(unloadedModulesTree.tree), gridBag.next().weighty(1.0).fillCell())
    mainPanel.add(statusLabel, gridBag.nextLine().next().coverLine().anchor(GridBagConstraints.WEST))

    return mainPanel
  }

  private fun moveAllNodes(from: ModuleDescriptionsTree, to: ModuleDescriptionsTree) {
    from.removeAllNodes()
    to.fillTree(moduleDescriptions.values)
    IdeFocusManager.getInstance(project).requestFocus(to.tree, true).doWhenDone {
      to.tree.selectionPath = to.tree.getPathForRow(0)
    }
  }

  private fun moveSelectedNodes(from: ModuleDescriptionsTree, to: ModuleDescriptionsTree) {
    val selected = from.selectedModules
    val oldSelectedRow = from.tree.selectionModel.leadSelectionRow
    from.removeModules(selected)
    val modules = to.addModules(selected)
    modules.firstOrNull()?.let { TreeUtil.selectNode(to.tree, it)}
    IdeFocusManager.getInstance(project).requestFocus(from.tree, true).doWhenDone {
      from.tree.selectionModel.selectionPath = from.tree.getPathForRow(oldSelectedRow.coerceAtMost(from.tree.rowCount-1))
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return loadedModulesTree.tree
  }

  override fun doOKAction() {
    ModuleManager.getInstance(project).setUnloadedModules(unloadedModulesTree.getAllModules().map { it.name })
    super.doOKAction()
  }
}

private class ModuleDescriptionsTree(project: Project) {
  private val root = RootNode()
  private val model = DefaultTreeModel(root)
  private val helper = createModuleDescriptionHelper(project)
  internal val tree = Tree(model)

  init {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    TreeSpeedSearch(tree, { treePath -> (treePath.lastPathComponent as? ModuleDescriptionTreeNode)?.text ?: "" }, true)
    tree.cellRenderer = ModuleDescriptionTreeRenderer()
  }

  val selectedModules: List<ModuleDescription>
    get() = tree.selectionPaths?.mapNotNull { (it.lastPathComponent as? ModuleDescriptionNode)?.moduleDescription } ?: emptyList()

  fun getAllModules(): List<ModuleDescription> {
    val modules = ArrayList<ModuleDescription>()
    TreeUtil.traverse(root, { node ->
      if (node is ModuleDescriptionNode) {
        modules.add(node.moduleDescription)
      }
      return@traverse true
    })
    return modules
  }

  fun fillTree(modules: Collection<ModuleDescription>) {
    helper.createModuleNodes(modules, root, model)
    tree.expandPath(TreePath(root))
  }

  fun addModules(modules: List<ModuleDescription>): List<ModuleDescriptionTreeNode> {
    return modules.map { helper.createModuleNode(it, root, model) }
  }

  fun removeModules(modules: List<ModuleDescription>) {
    val names = modules.mapTo(HashSet<String>()) { it.name }
    val toRemove = findNodes { it.moduleDescription.name in names }
    for (node in toRemove) {
      helper.removeNode(node, root, model)
    }
  }

  private fun findNodes(condition: (ModuleDescriptionNode) -> Boolean): List<ModuleDescriptionNode> {
    val result = ArrayList<ModuleDescriptionNode>()
    TreeUtil.traverse(root, { node ->
      if (node is ModuleDescriptionNode && condition(node)) {
        result.add(node)
      }
      return@traverse true
    })
    return result
  }

  fun removeAllNodes() {
    helper.removeAllNodes(root, model)
  }

  fun selectNodes(moduleNames: Set<String>) {
    val toSelect = findNodes { it.moduleDescription.name in moduleNames }
    val paths = toSelect.map { TreeUtil.getPath(root, it) }
    paths.forEach { tree.expandPath(it) }
    tree.selectionModel.selectionPaths = paths.toTypedArray()
    if (paths.isNotEmpty()) {
      TreeUtil.showRowCentered(tree, tree.getRowForPath(paths.first()), false, true)
    }
  }
}

private class ModuleDescriptionTreeRenderer : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
    if (value is ModuleDescriptionTreeNode) {
      icon = value.icon
      append(value.text)
    }
  }
}

private fun createModuleDescriptionHelper(project: Project): ModuleGroupingTreeHelper<ModuleDescription, ModuleDescriptionTreeNode> {
  val moduleGrouper = ModuleGrouper.instanceFor(project)
  return ModuleGroupingTreeHelper.forEmptyTree(true, ModuleDescriptionGrouping(moduleGrouper),
                                               ::ModuleGroupNode, {ModuleDescriptionNode(it, moduleGrouper)}, nodeComparator)
}

private class ModuleDescriptionGrouping(private val moduleGrouper: ModuleGrouper) : ModuleGroupingImplementation<ModuleDescription> {
  override fun getGroupPath(m: ModuleDescription): List<String> {
    return moduleGrouper.getGroupPath(m)
  }

  override fun getModuleAsGroupPath(m: ModuleDescription): List<String>? {
    return moduleGrouper.getModuleAsGroupPath(m)
  }
}

private val nodeComparator = compareBy(NaturalComparator.INSTANCE) { node: ModuleDescriptionTreeNode -> node.text }
private interface ModuleDescriptionTreeNode : MutableTreeNode {
  val text: String
  val icon: Icon
}

private class ModuleDescriptionNode(val moduleDescription: ModuleDescription, val moduleGrouper: ModuleGrouper) : DefaultMutableTreeNode(), ModuleDescriptionTreeNode {
  override val text: String
    get() = moduleGrouper.getShortenedNameByFullModuleName(moduleDescription.name)

  override val icon: Icon
    get() = AllIcons.Nodes.Module
}

private class ModuleGroupNode(val group: ModuleGroup) : DefaultMutableTreeNode(), ModuleDescriptionTreeNode {
  override val text: String
    get() = group.presentableText()

  override val icon: Icon
    get() = AllIcons.Nodes.ModuleGroup
}

private class RootNode: DefaultMutableTreeNode(), ModuleDescriptionTreeNode {
  override val text: String
    get() = "<root>"

  override val icon: Icon
    get() = AllIcons.Nodes.ModuleGroup
}