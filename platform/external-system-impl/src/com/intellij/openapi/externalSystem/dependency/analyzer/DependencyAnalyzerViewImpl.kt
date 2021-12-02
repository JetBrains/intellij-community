// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.*
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class DependencyAnalyzerViewImpl(
  project: Project,
  systemId: ProjectSystemId,
  parentDisposable: Disposable
) : DependencyAnalyzerView {
  override val component: JComponent

  private val contributor = DependencyAnalyzerExtension.getExtension(project, systemId)
  private val iconsProvider = ExternalSystemIconProvider.getExtension(systemId)

  private val propertyGraph = PropertyGraph(isBlockPropagation = false)

  private val externalProjectProperty = propertyGraph.graphProperty<String?> { null }
  private val dependencyDataFilterProperty = propertyGraph.graphProperty { "" }
  private val dependencyScopeFilterProperty = propertyGraph.graphProperty { emptyList<ScopeItem>() }
  private val showDependencyWarningsProperty = propertyGraph.graphProperty { false }
  private val showDependencyGroupIdProperty = propertyGraph.graphProperty { false }
    .bindWithBooleanStorage(SHOW_GROUP_ID_PROPERTY)

  private val dependencyProperty = propertyGraph.graphProperty<Dependency?> { null }
  private val dependencyGroupProperty = dependencyProperty.transform(::findDependencyGroup, ::findDependency)
  private val showDependencyTreeProperty = propertyGraph.graphProperty { false }
    .bindWithBooleanStorage(SHOW_AS_TREE_PROPERTY)

  private val usagesTitleProperty = propertyGraph.graphProperty(::getUsagesTitle)

  private var externalProjectPath by externalProjectProperty
  private var dependencyDataFilter by dependencyDataFilterProperty
  private var dependencyScopeFilter by dependencyScopeFilterProperty
  private var showDependencyWarnings by showDependencyWarningsProperty
  private var showDependencyGroupId by showDependencyGroupIdProperty

  private var dependency by dependencyProperty
  private var dependencyGroup by dependencyGroupProperty
  private var showDependencyTree by showDependencyTreeProperty

  private val externalProjects = ArrayList<ExternalProject>()
  private val dependencyGroups = ArrayList<DependencyGroup>()
  private val dependencyMap = LinkedHashMap<Dependency, DependencyGroup>()

  private val dependencyListModel = CollectionListModel<DependencyGroup>()
  private val dependencyTreeModel = DefaultTreeModel(null)
  private val usagesTreeModel = DefaultTreeModel(null)

  override fun setSelectedExternalProject(externalProjectPath: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    this.externalProjectPath = externalProjectPath
  }

  override fun setSelectedDependency(externalProjectPath: String, dependency: Dependency) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    setSelectedExternalProject(externalProjectPath)
    this.dependency = dependency
  }

  private fun updateViewModel() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    updateExternalProjectsModel()
  }

  private fun getExternalProjects(): List<ExternalProject> {
    return externalProjects
  }

  private fun findExternalProject(externalProjectPath: String?): ExternalProject? {
    return getExternalProjects().find { it.path == externalProjectPath }
  }

  private fun findDependencyGroup(dependency: Dependency?): DependencyGroup? {
    return dependency?.let { dependencyMap[it] }
  }

  private fun findDependency(dependencyGroup: DependencyGroup?): Dependency? {
    return dependencyGroup
      ?.let { filterDependencies(it.variances) + it.variances }
      ?.first()
  }

  private fun filterDependencies(dependencies: Iterable<Dependency>): List<Dependency> {
    val dependencyDataFilter = dependencyDataFilter
    val dependencyScopeFilter = dependencyScopeFilter.filter { it.isSelected }.map { it.scope }
    val showDependencyWarnings = showDependencyWarnings
    return dependencies
      .filter { dependency -> dependencyDataFilter in dependency.data.displayText }
      .filter { dependency -> dependency.scope in dependencyScopeFilter }
      .filter { dependency -> if (showDependencyWarnings) dependency.status.any { it is Status.Warning } else true }
  }

  private fun getDependencyPath(dependency: Dependency): List<Dependency> {
    val dependencyPath = ArrayList<Dependency>()
    var current: Dependency? = dependency
    while (current != null) {
      dependencyPath.add(current)
      current = current.usage
    }
    return dependencyPath
  }

  private fun updateExternalProjectsModel() {
    externalProjects.clear()
    contributor.getExternalProjects()
      .forEach { externalProjects.add(it) }

    val externalProject = externalProjects.find { it.path == externalProjectPath }
                          ?: externalProjects.firstOrNull()
    externalProjectPath = externalProject?.path
  }

  private fun updateScopesModel() {
    val scopes = externalProjectPath?.let(contributor::getDependencyScopes) ?: emptyList()
    val scopesIndex = dependencyScopeFilter.associate { it.scope.id to it.isSelected }
    val isAny = scopesIndex.all { it.value }
    dependencyScopeFilter = scopes.map { ScopeItem(it, scopesIndex[it.id] ?: isAny) }
  }

  private fun updateDependencyModel() {
    dependencyMap.clear()
    dependencyGroups.clear()
    externalProjectPath?.let { externalProjectPath ->
      dependencyGroups.addAll(
        contributor.getDependencies(externalProjectPath)
          .groupBy { it.data.group }
          .map { DependencyGroup(it.value) }
      )
      for (group in dependencyGroups) {
        for (dependency in group.variances) {
          dependencyMap[dependency] = group
        }
      }
    }

    val filteredDependencyGroups = dependencyGroups.filter { filterDependencies(it.variances).isNotEmpty() }
    dependencyListModel.replaceAll(filteredDependencyGroups)

    val filteredDependencies = filterDependencies(dependencyMap.keys)
    dependencyTreeModel.setRoot(buildTree(filteredDependencies))

    dependency = filteredDependencies.find { it == dependency }
                 ?: filteredDependencies.firstOrNull()
  }

  private fun updateUsagesModel() {
    val dependencies = dependencyGroup?.variances ?: emptyList()
    usagesTreeModel.setRoot(buildTree(dependencies))
  }

  private fun buildTree(dependencies: List<Dependency>): DefaultMutableTreeNode? {
    val dependenciesForTree = dependencies.flatMap { getDependencyPath(it) }.toSet()

    if (dependenciesForTree.isEmpty()) {
      return null
    }
    val rootDependency = dependenciesForTree.singleOrNull { it.usage == null }
    if (rootDependency == null) {
      val rawTree = dependenciesForTree.joinToString("\n") { "${it.usage} -> ${it.data}" }
      logger<DependencyAnalyzerView>().error("Cannot determine root of dependency tree:\n$rawTree")
      return null
    }

    val nodeMap = LinkedHashMap<Dependency, MutableList<Dependency>>()
    for (dependency in dependenciesForTree) {
      val usage = dependency.usage ?: continue
      val children = nodeMap.getOrPut(usage) { ArrayList() }
      children.add(dependency)
    }

    val rootNode = DefaultMutableTreeNode(rootDependency)
    val queue = ArrayDeque<DefaultMutableTreeNode>()
    queue.addLast(rootNode)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val dependency = node.userObject as Dependency
      val children = nodeMap[dependency] ?: continue
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
      externalProjectProperty.transform(::findExternalProject) { it?.path },
      externalProjects,
      iconsProvider
    )
    val dataFilterField = SearchTextField(SEARCH_HISTORY_PROPERTY)
      .apply { setPreferredWidth(JBUI.scale(240)) }
      .apply { textEditor.bind(dependencyDataFilterProperty) }
    val scopeFilterSelector = SearchScopeSelector(dependencyScopeFilterProperty)
    val dependencyInspectionFilterAction = toggleAction(showDependencyWarningsProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.conflicts.show") }
      .apply { templatePresentation.icon = AllIcons.General.ShowWarning }
    val showDependencyGroupIdAction = toggleAction(showDependencyGroupIdProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.groupId.show") }
    val viewOptionsActionGroup = popupActionGroup(showDependencyGroupIdAction)
      .apply { templatePresentation.icon = AllIcons.Actions.Show }

    val dependencyTitle = label(ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.title"))
    val dependencyList = JBList(dependencyListModel)
      .apply { cellRenderer = DependencyGroupRenderer() }
      .apply { emptyText.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty") }
      .bind(dependencyGroupProperty)
    val dependencyTree = SimpleTree(dependencyTreeModel)
      .apply { cellRenderer = DependencyRenderer() }
      .apply { emptyText.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty") }
      .bind(dependencyProperty)
    val showDependencyTreeAction = toggleAction(showDependencyTreeProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.tree.show") }
      .apply { templatePresentation.icon = AllIcons.Actions.ShowAsTree }
    val expandDependencyTreeAction = expandTreeAction(dependencyTree)
      .whenActionUpdated { it.presentation.isEnabled = showDependencyTree }
    val collapseDependencyTreeAction = collapseTreeAction(dependencyTree)
      .whenActionUpdated { it.presentation.isEnabled = showDependencyTree }

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
          dataFilterField,
          scopeFilterSelector,
          labelSeparator(),
          actionToolbarPanel(
            dependencyInspectionFilterAction,
            viewOptionsActionGroup
          )
        ))
      }
      setContent(horizontalSplitPanel(SPLIT_VIEW_PROPORTION_PROPERTY, 0.5f) {
        firstComponent = toolWindowPanel {
          toolbar = toolbarPanel {
            addToLeft(dependencyTitle)
            addToRight(actionToolbarPanel(
              showDependencyTreeAction,
              actionSeparator(),
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
        }
        secondComponent = toolWindowPanel {
          toolbar = toolbarPanel {
            addToLeft(usagesTitle)
            addToRight(actionToolbarPanel(
              expandUsagesTreeAction,
              collapseUsagesTreeAction
            ))
          }
          setContent(ScrollPaneFactory.createScrollPane(usagesTree, true))
        }
      })
    }
  }

  init {
    usagesTitleProperty.dependsOn(dependencyGroupProperty)
    usagesTitleProperty.dependsOn(showDependencyGroupIdProperty)

    externalProjectProperty.afterChange { updateScopesModel() }
    externalProjectProperty.afterChange { updateDependencyModel() }
    dependencyGroupProperty.afterChange { updateUsagesModel() }
    dependencyDataFilterProperty.afterChange { updateDependencyModel() }
    dependencyScopeFilterProperty.afterChange { updateDependencyModel() }
    showDependencyWarningsProperty.afterChange { updateDependencyModel() }
    showDependencyGroupIdProperty.afterChange { updateDependencyModel() }

    contributor.whenDataChanged({
      invokeLater {
        updateViewModel()
      }
    }, parentDisposable)
    updateViewModel()
  }

  private fun JTree.bind(property: ObservableClearableProperty<Dependency?>) = apply {
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
        property.set(node?.userObject as? Dependency)
      }
    }
  }

  private fun DefaultMutableTreeNode.getDependencyPath(dependency: Dependency): TreePath? {
    for (node in depthFirstEnumeration()) {
      if (node is DefaultMutableTreeNode) {
        if (dependency == node.userObject) {
          return TreePath(node.path)
        }
      }
    }
    return null
  }

  private fun getUsagesTitle(): @NlsContexts.Label String {
    val data = dependency?.data ?: return ""
    return ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", data.displayText)
  }

  private val Dependency.Data.group: String
    get() = when (this) {
      is Dependency.Data.Module -> name
      is Dependency.Data.Artifact -> "$groupId:$artifactId"
    }

  private val Dependency.Data.displayText: @NlsSafe String
    get() = when (this) {
      is Dependency.Data.Module -> name
      is Dependency.Data.Artifact ->
        if (showDependencyGroupId) {
          "$groupId:$artifactId:$version"
        }
        else {
          "$artifactId:$version"
        }
    }

  private fun expandAllWhenStructureChanged(tree: JTree) {
    tree.model.addTreeModelListener(
      TreeModelAdapter.create { _, _ ->
        invokeLater {
          TreeUtil.expandAll(tree)
        }
      }
    )
  }

  private inner class DependencyGroupRenderer : ColoredListCellRenderer<DependencyGroup>() {
    override fun customizeCellRenderer(
      list: JList<out DependencyGroup>,
      value: DependencyGroup?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      val group = value ?: return
      val dependency = findDependency(group)
      val data = dependency?.data ?: return
      val variances = filterDependencies(group.variances)
      val status = variances.flatMap { it.status }
      val scopes = variances.map { it.scope.name }.toSet()
      icon = when {
        status.any { it is Status.Warning } -> AllIcons.General.Warning
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
      val dependency = node.userObject as? Dependency ?: return
      val data = dependency.data
      val status = dependency.status
      icon = when {
        status.any { it is Status.Warning } -> AllIcons.General.Warning
        data is Dependency.Data.Module -> AllIcons.Nodes.Module
        data is Dependency.Data.Artifact -> AllIcons.Nodes.PpLib
        else -> throw UnsupportedOperationException()
      }
      if (Status.Omitted in status) {
        append(data.displayText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      else {
        append(data.displayText)
      }
      append(" (${dependency.scope.name})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
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
      val dependency = node.userObject as? Dependency ?: return
      val warning = dependency.status
        .filterIsInstance<Status.Warning>()
        .firstOrNull()
      if (warning != null) {
        append(" ${warning.message}", SimpleTextAttributes.ERROR_ATTRIBUTES)
      }
    }
  }

  private data class DependencyGroup(val variances: List<Dependency>)

  companion object {
    private val SEARCH_HISTORY_PROPERTY = DependencyAnalyzerView::class.java.name + ".search"
    private val SHOW_GROUP_ID_PROPERTY = DependencyAnalyzerView::class.java.name + ".showGroupId"
    private val SHOW_AS_TREE_PROPERTY = DependencyAnalyzerView::class.java.name + ".showAsTree"
    private val SPLIT_VIEW_PROPORTION_PROPERTY = DependencyAnalyzerView::class.java.name + ".splitProportion"
  }
}
