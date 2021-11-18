// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.DependencyGroup
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.InspectionResult.Omitted
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.InspectionResult.VersionConflict
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.ObservableClearableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.lockOrSkip
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlin.collections.ArrayDeque

class DependencyAnalyzerViewImpl(
  project: Project,
  systemId: ProjectSystemId,
  parentDisposable: Disposable
) : DependencyAnalyzerView {
  override val component: JComponent

  private val dependencyContributor = DependencyAnalyzerExtension.getExtension(project, systemId)
  private val iconsProvider = ExternalSystemIconProvider.getExtension(systemId)

  private val propertyGraph = PropertyGraph(isBlockPropagation = false)

  private val externalProjectProperty = propertyGraph.graphProperty<String?> { null }
  private val searchDataProperty = propertyGraph.graphProperty { "" }
  private val searchScopeProperty = propertyGraph.graphProperty { emptyList<ScopeItem>() }
  private val dependencyProperty = propertyGraph.graphProperty<Dependency.Data?> { null }

  private val showDependencyTreeProperty = propertyGraph.graphProperty { false }
  private val showGroupIdProperty = propertyGraph.graphProperty { false }

  private val usagesTitleProperty = propertyGraph.graphProperty(::getUsagesTitle)

  private val externalProjectListModel = ArrayList<String>()
  private val dependencyGroupListModel = CollectionListModel<DependencyGroupItem>()
  private val dependencyTreeModel = DefaultTreeModel(DefaultMutableTreeNode())
  private val usagesTreeModel = DefaultTreeModel(DefaultMutableTreeNode())

  private var showDependencyTree by showDependencyTreeProperty
  private var showGroupId by showGroupIdProperty

  override fun setSelectedExternalProject(externalProjectPath: String) {
    externalProjectProperty.set(externalProjectPath)
  }

  override fun setSelectedDependency(externalProjectPath: String, dependency: Dependency) {
    setSelectedExternalProject(externalProjectPath)
    dependencyProperty.set(dependency.data)
  }

  private fun getDependencies(): List<DependencyItem> {
    return dependencyGroupListModel.items
      .flatMap { it.variances }
  }

  private fun findDependencyItem(dependency: Dependency?): DependencyItem? {
    return findDependencyItem(dependency?.data)
  }

  private fun findDependencyItem(data: Dependency.Data?): DependencyItem? {
    return getDependencies().find { it.dependency.data == data }
  }

  private fun findDependencyGroup(data: Dependency.Data?): DependencyGroupItem? {
    return findDependencyItem(data)?.group
  }

  private fun updateExternalProjectsModel() {
    externalProjectListModel.clear()
    dependencyContributor.getExternalProjectPaths()
      .forEach { externalProjectListModel.add(it) }

    val oldExternalProjectPath = externalProjectProperty.get()
    val externalProjectPath = externalProjectListModel.find { it == oldExternalProjectPath }
                              ?: externalProjectListModel.firstOrNull()
    externalProjectProperty.set(externalProjectPath)
  }

  private fun updateScopesModel() {
    val scopes = externalProjectProperty.get()
      ?.let(dependencyContributor::getDependencyScopes)
      ?: emptyList()
    val scopesIndex = searchScopeProperty.get()
      .associate { it.name to it.isSelected }
    val isAny = scopesIndex.all { it.value }
    searchScopeProperty.set(scopes.map { ScopeItem(it, scopesIndex[it] ?: isAny) })
  }

  private fun updateDependencyModel() {
    dependencyGroupListModel.removeAll()
    val externalProjectPath = externalProjectProperty.get()
    if (externalProjectPath != null) {
      val groups = dependencyContributor.getDependencyGroups(externalProjectPath)
        .map { DependencyGroupItem(externalProjectPath, it) }
      groups.forEach { dependencyGroupListModel.add(it) }
    }

    val dependencies = getDependencies()
    dependencyTreeModel.setRoot(buildTree(dependencies))

    val dependencyData = dependencyProperty.get()
    val dependencyItem = dependencies.find { it.dependency.data == dependencyData }
                         ?: dependencies.firstOrNull()
    dependencyProperty.set(dependencyItem?.dependency?.data)
  }

  private fun updateUsagesModel() {
    val dependencies = ArrayList<DependencyItem>()
    val externalProjectPath = externalProjectProperty.get()
    val group = findDependencyGroup(dependencyProperty.get())
    if (externalProjectPath != null && group != null) {
      for (candidate in group.variances) {
        var current: DependencyItem? = candidate
        while (current != null) {
          dependencies.add(current)
          current = findDependencyItem(current.dependency.usage)
        }
      }
    }
    usagesTreeModel.setRoot(buildTree(dependencies))
  }

  private fun buildTree(dependencies: List<DependencyItem>): TreeNode {
    if (dependencies.isEmpty()) {
      return DefaultMutableTreeNode()
    }

    val dependenciesSet = Collections.newSetFromMap<DependencyItem>(IdentityHashMap())
    dependenciesSet.addAll(dependencies)

    val rootDependency = dependenciesSet.singleOrNull { it.dependency.usage == null }
    if (rootDependency == null) {
      val rawTree = dependenciesSet.joinToString("\n") { "${it.dependency.usage} -> ${it.dependency.data}" }
      logger<DependencyAnalyzerView>().error("Cannot determine root of dependency tree:\n$rawTree")
      return DefaultMutableTreeNode()
    }

    val nodeMap = IdentityHashMap<Dependency, MutableList<DependencyItem>>()
    for (item in dependenciesSet) {
      val dependency = item.dependency.usage ?: continue
      val children = nodeMap.getOrPut(dependency) { ArrayList() }
      children.add(item)
    }

    val rootNode = DefaultMutableTreeNode(rootDependency)
    val queue = ArrayDeque<DefaultMutableTreeNode>()
    queue.addLast(rootNode)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val item = node.userObject as DependencyItem
      val children = nodeMap[item.dependency] ?: continue
      for (child in children) {
        val childNode = DefaultMutableTreeNode(child)
        node.add(childNode)
        queue.addLast(childNode)
      }
    }
    return rootNode
  }

  init {
    val externalProjectSelector = ExternalProjectSelector(
      externalProjectProperty,
      externalProjectListModel,
      dependencyContributor,
      iconsProvider
    )
    val searchDataField = JTextField().bind(searchDataProperty)
    val searchScopeSelector = SearchScopeSelector(searchScopeProperty)

    val dependencyTitle = label(ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.title"))
    val dependencyList = JBList(dependencyGroupListModel)
      .apply { cellRenderer = DependencyGroupRenderer() }
      .apply { emptyText.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty") }
      .bind(dependencyProperty.transform(::findDependencyGroup) { it?.group?.data })
    val dependencyTree = SimpleTree(dependencyTreeModel)
      .apply { cellRenderer = DependencyRenderer() }
      .apply { emptyText.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty") }
      .bind(dependencyProperty.transform(::findDependencyItem) { it?.dependency?.data })
    val showDependencyTreeAction = toggleAction(showDependencyTreeProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.tree.show") }
      .apply { templatePresentation.icon = AllIcons.Actions.ShowAsTree }
    val expandDependencyTreeAction = expandTreeAction(dependencyTree) { it.presentation.isEnabled = showDependencyTree }
    val collapseDependencyTreeAction = collapseTreeAction(dependencyTree) { it.presentation.isEnabled = showDependencyTree }

    val usagesTitle = label(usagesTitleProperty)
    val usagesTree = SimpleTree(usagesTreeModel)
      .apply { cellRenderer = UsagesRenderer() }
      .apply { emptyText.text = "" }
      .apply { expandAllWhenStructureChanged(this) }
    val expandUsagesTreeAction = expandTreeAction(usagesTree)
    val collapseUsagesTreeAction = collapseTreeAction(usagesTree)

    component = toolWindowPanel {
      toolbar = toolbarPanel {
        addToLeft(horizontalPanel(
          externalProjectSelector,
          searchDataField,
          searchScopeSelector
        ))
      }
      setContent(splitPanel(
        toolWindowPanel {
          toolbar = toolbarPanel {
            addToLeft(dependencyTitle)
            addToRight(actionToolbarPanel(
              showDependencyTreeAction,
              Separator(),
              expandDependencyTreeAction,
              collapseDependencyTreeAction
            ))
          }
          setContent(cardPanel(showDependencyTreeProperty) { showDependencyTree ->
            when (showDependencyTree) {
              true -> ScrollPaneFactory.createScrollPane(dependencyTree, true)
              else -> ScrollPaneFactory.createScrollPane(dependencyList, true)
            }
          })
        },
        toolWindowPanel {
          toolbar = toolbarPanel {
            addToLeft(usagesTitle)
            addToRight(actionToolbarPanel(
              expandUsagesTreeAction,
              collapseUsagesTreeAction
            ))
          }
          setContent(ScrollPaneFactory.createScrollPane(usagesTree, true))
        }
      ))
    }
  }

  init {
    usagesTitleProperty.dependsOn(dependencyProperty)
    usagesTitleProperty.dependsOn(showGroupIdProperty)
    externalProjectProperty.afterChange {
      updateScopesModel()
      updateDependencyModel()
    }
    dependencyProperty.afterChange {
      updateUsagesModel()
    }
    dependencyContributor.whenDataChanged({
      updateExternalProjectsModel()
    }, parentDisposable)
    updateExternalProjectsModel()
  }

  private fun JTree.bind(property: ObservableClearableProperty<DependencyItem?>) = apply {
    val mutex = AtomicBoolean()
    property.afterChange {
      mutex.lockOrSkip {
        val dependency = property.get()
        val root = model.root as? DefaultMutableTreeNode
        if (root != null && dependency != null) {
          selectionPath = root.getDependencyPath(dependency)
        }
        else {
          selectionPath = null
        }
      }
    }
    addTreeSelectionListener {
      mutex.lockOrSkip {
        val node = lastSelectedPathComponent as? DefaultMutableTreeNode
        property.set(node?.userObject as? DependencyItem)
      }
    }
  }

  private fun DefaultMutableTreeNode.getDependencyPath(dependencyItem: DependencyItem): TreePath? {
    for (node in depthFirstEnumeration()) {
      if (node is DefaultMutableTreeNode) {
        if (node.userObject === dependencyItem) {
          return TreePath(node.path)
        }
      }
    }
    return null
  }

  private fun getUsagesTitle(): @NlsContexts.Label String {
    val dependency = dependencyProperty.get() ?: return ""
    return ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", dependency.displayText)
  }

  private val Dependency.Data.displayText: @NlsSafe String
    get() = when (this) {
      is Dependency.Data.Module -> name
      is Dependency.Data.Artifact ->
        if (showGroupId) {
          "$groupId:$artifactId:$version"
        }
        else {
          "$artifactId:$version"
        }
    }

  private fun expandAllWhenStructureChanged(tree: JTree) {
    tree.model.addTreeModelListener(
      TreeModelAdapter.create { _, t ->
        if (t == TreeModelAdapter.EventType.StructureChanged) {
          invokeLater {
            TreeUtil.expandAll(tree)
          }
        }
      }
    )
  }

  private inner class DependencyGroupRenderer : ColoredListCellRenderer<DependencyGroupItem>() {
    override fun customizeCellRenderer(
      list: JList<out DependencyGroupItem>,
      value: DependencyGroupItem?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      val item = value ?: return
      val data = item.group.data
      val inspectionResult = item.variances.flatMap { it.inspectionResult }
      val scopes = item.variances.map { it.scope }.toSet()
      icon = when {
        inspectionResult.any { it is VersionConflict } -> AllIcons.General.Warning
        data is Dependency.Data.Module -> AllIcons.Nodes.Module
        data is Dependency.Data.Artifact -> AllIcons.Nodes.PpLib
        else -> throw UnsupportedOperationException()
      }
      append(data.displayText)
      val scopesText = scopes.singleOrNull() ?: ExternalSystemBundle.message("external.system.dependency.analyzer.scope.n", scopes.size)
      append(" ($scopesText)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }

  private open inner class DependencyRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      val node = value as? DefaultMutableTreeNode ?: return
      val item = node.userObject as? DependencyItem ?: return
      val data = item.dependency.data
      val inspectionResult = item.inspectionResult
      icon = when {
        inspectionResult.any { it is VersionConflict } -> AllIcons.General.Warning
        data is Dependency.Data.Module -> AllIcons.Nodes.Module
        data is Dependency.Data.Artifact -> AllIcons.Nodes.PpLib
        else -> throw UnsupportedOperationException()
      }
      if (Omitted in inspectionResult) {
        append(data.displayText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      else {
        append(data.displayText)
        append(" (${item.scope})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

  private inner class UsagesRenderer : DependencyRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
      val node = value as? DefaultMutableTreeNode ?: return
      val item = node.userObject as? DependencyItem ?: return
      val versionConflict = item.inspectionResult.filterIsInstance<VersionConflict>().firstOrNull()
      if (versionConflict != null) {
        val version = versionConflict.conflicted.version
        val message = ExternalSystemBundle.message("external.system.dependency.analyzer.error.version.conflict", version)
        append(" $message", SimpleTextAttributes.ERROR_ATTRIBUTES)
      }
    }
  }

  private inner class DependencyGroupItem(
    val externalProjectPath: String,
    val group: DependencyGroup
  ) {
    val variances by lazy { group.variances.map { DependencyItem(externalProjectPath, this, it) } }

    override fun toString() = group.toString()
  }

  private inner class DependencyItem(
    val externalProjectPath: String,
    val group: DependencyGroupItem,
    val dependency: Dependency
  ) {
    val scope by lazy { dependencyContributor.getDependencyScope(externalProjectPath, dependency) }
    val inspectionResult by lazy { dependencyContributor.getInspectionResult(externalProjectPath, dependency) }

    override fun toString() = dependency.toString()
  }
}
