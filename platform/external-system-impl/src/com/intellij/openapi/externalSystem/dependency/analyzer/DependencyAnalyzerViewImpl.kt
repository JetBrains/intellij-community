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
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.asProperty
import com.intellij.openapi.observable.properties.AtomicObservableProperty
import com.intellij.openapi.observable.properties.ObservableClearableProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.observable.properties.and
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.whenStructureChanged
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.layout.*
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.lockOrSkip
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class DependencyAnalyzerViewImpl(
  private val project: Project,
  private val systemId: ProjectSystemId,
  private val parentDisposable: Disposable
) : DependencyAnalyzerView {
  override val component: JComponent

  private val iconsProvider = ExternalSystemIconProvider.getExtension(systemId)
  private val contributor = DependencyAnalyzerExtension.EP_NAME.extensionList
    .firstNotNullOf { it.createContributor(project, systemId, parentDisposable) }
  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("DependencyAnalyzerView.backgroundExecutor", 1)

  private val dependencyLoadingOperation = AnonymousParallelOperationTrace("DA: Dependency loading")
  private val dependencyLoadingProperty = dependencyLoadingOperation.asProperty()

  private val externalProjectPathProperty = AtomicObservableProperty<String?>(null)
  private val externalProjectProperty = externalProjectPathProperty.transform(::findExternalProject) { it?.path }
  private val dependencyDataFilterProperty = AtomicObservableProperty("")
  private val dependencyScopeFilterProperty = AtomicObservableProperty(emptyList<ScopeItem>())
  private val showDependencyWarningsProperty = AtomicObservableProperty(false)
  private val showDependencyGroupIdProperty = AtomicObservableProperty(false)
    .bindWithBooleanStorage(SHOW_GROUP_ID_PROPERTY)

  private val dependencyModelProperty = AtomicObservableProperty(DependencyModel.EMPTY)
  private val dependencyProperty = AtomicObservableProperty<Dependency?>(null)
  private val dependencyGroupProperty = dependencyProperty.transform(::findDependencyGroup, ::findDependency)
  private val showDependencyTreeProperty = AtomicObservableProperty(false)
    .bindWithBooleanStorage(SHOW_AS_TREE_PROPERTY)
  private val dependencyEmptyTextProperty = AtomicObservableProperty("")
  private val usagesTitleProperty = AtomicObservableProperty("")

  private var externalProjectPath by externalProjectPathProperty
  private var dependencyDataFilter by dependencyDataFilterProperty
  private var dependencyScopeFilter by dependencyScopeFilterProperty
  private var showDependencyWarnings by showDependencyWarningsProperty
  private var showDependencyGroupId by showDependencyGroupIdProperty

  private var dependencyModel by dependencyModelProperty
  private var dependency by dependencyProperty
  private var dependencyGroup by dependencyGroupProperty
  private var dependencyEmptyState by dependencyEmptyTextProperty
  private var usagesTitle by usagesTitleProperty

  private val externalProjects = ArrayList<ExternalProject>()

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
    invokeLater {
      updateExternalProjectsModel()
    }
  }

  private fun getExternalProjects(): List<ExternalProject> {
    return externalProjects
  }

  private fun findExternalProject(externalProjectPath: String?): ExternalProject? {
    return getExternalProjects().find { it.path == externalProjectPath }
  }

  private fun findDependencyGroup(dependency: Dependency?): DependencyGroup? {
    return dependencyModel[dependency]
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
    executeLoadingTask(
      onBackgroundThread = {
        contributor.getExternalProjects()
      },
      onUiThread = { projects ->
        externalProjects.addAll(projects)

        val externalProject = externalProjects.find { it.path == externalProjectPath }
                              ?: externalProjects.firstOrNull()
        externalProjectPath = externalProject?.path
      }
    )
  }

  private fun updateScopesModel() {
    executeLoadingTask(
      onBackgroundThread = {
        externalProjectPath?.let(contributor::getDependencyScopes) ?: emptyList()
      },
      onUiThread = { scopes ->
        val scopesIndex = dependencyScopeFilter.associate { it.scope.id to it.isSelected }
        val isAny = scopesIndex.all { it.value }
        dependencyScopeFilter = scopes.map { ScopeItem(it, scopesIndex[it.id] ?: isAny) }
      }
    )
  }

  private fun updateDependencyModel() {
    dependencyModel = DependencyModel.EMPTY
    executeLoadingTask(
      onBackgroundThread = {
        externalProjectPath?.let(contributor::getDependencies) ?: emptyList()
      },
      onUiThread = {
        dependencyModel = DependencyModel(it)
      }
    )
  }

  private fun updateFilteredDependencyModel() {
    val filteredDependencyGroups = dependencyModel.dependencyGroups
      .filter { filterDependencies(it.variances).isNotEmpty() }
    dependencyListModel.replaceAll(filteredDependencyGroups)

    val filteredDependencies = filterDependencies(dependencyModel.dependencies)
    dependencyTreeModel.setRoot(buildTree(filteredDependencies))

    dependency = filteredDependencies.find { it == dependency }
                 ?: filteredDependencies.firstOrNull()
  }

  private fun updateDependencyEmptyState() {
    dependencyEmptyState = when {
      !dependencyLoadingOperation.isOperationCompleted() -> ""
      else -> ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty")
    }
  }

  private fun updateUsagesTitle() {
    val text = dependency?.data?.displayText
    usagesTitle = if (text == null) "" else ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", text)
  }

  private fun updateUsagesModel() {
    val dependencies = dependencyGroup?.variances ?: emptyList()
    usagesTreeModel.setRoot(buildTree(dependencies))
  }

  private fun <R> executeLoadingTask(onBackgroundThread: () -> R, onUiThread: (R) -> Unit) {
    dependencyLoadingOperation.startTask()
    BackgroundTaskUtil.execute(backgroundExecutor, parentDisposable) {
      val result = onBackgroundThread()
      invokeLater {
        onUiThread(result)
        dependencyLoadingOperation.finishTask()
      }
    }
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
    val externalProjectSelector = ExternalProjectSelector(externalProjectProperty, externalProjects, iconsProvider)
      .bindEnabled(dependencyLoadingProperty)
    val dataFilterField = SearchTextField(SEARCH_HISTORY_PROPERTY)
      .apply { setPreferredWidth(JBUI.scale(240)) }
      .apply { textEditor.bind(dependencyDataFilterProperty) }
      .bindEnabled(dependencyLoadingProperty)
    val scopeFilterSelector = SearchScopeSelector(dependencyScopeFilterProperty)
      .bindEnabled(dependencyLoadingProperty)
    val dependencyInspectionFilterButton = toggleAction(showDependencyWarningsProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.conflicts.show") }
      .apply { templatePresentation.icon = AllIcons.General.ShowWarning }
      .asActionButton()
      .bindEnabled(dependencyLoadingProperty)
    val showDependencyGroupIdAction = toggleAction(showDependencyGroupIdProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.groupId.show") }
    val viewOptionsButton = popupActionGroup(showDependencyGroupIdAction)
      .apply { templatePresentation.icon = AllIcons.Actions.Show }
      .asActionButton()
      .bindEnabled(dependencyLoadingProperty)

    val dependencyTitle = label(ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.title"))
    val dependencyList = JBList(dependencyListModel)
      .apply { cellRenderer = DependencyGroupRenderer() }
      .bindEmptyText(dependencyEmptyTextProperty)
      .bind(dependencyGroupProperty)
      .bindEnabled(dependencyLoadingProperty)
    val dependencyTree = SimpleTree(dependencyTreeModel)
      .apply { cellRenderer = DependencyRenderer() }
      .bindEmptyText(dependencyEmptyTextProperty)
      .bind(dependencyProperty)
      .bindEnabled(dependencyLoadingProperty)
    val dependencyPanel = cardPanel<Boolean> { if (it) dependencyTree else dependencyList }
      .bind(showDependencyTreeProperty)
    val dependencyLoadingPanel = JBLoadingPanel(BorderLayout(), parentDisposable)
      .apply { add(dependencyPanel, BorderLayout.CENTER) }
      .apply { setLoadingText(ExternalSystemBundle.message("external.system.dependency.analyzer.dependency.loading")) }
      .bind(dependencyLoadingOperation)
    val showDependencyTreeButton = toggleAction(showDependencyTreeProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.tree.show") }
      .apply { templatePresentation.icon = AllIcons.Actions.ShowAsTree }
      .asActionButton()
      .bindEnabled(dependencyLoadingProperty)
    val expandDependencyTreeButton = expandTreeAction(dependencyTree)
      .asActionButton()
      .bindEnabled(showDependencyTreeProperty and dependencyLoadingProperty)
    val collapseDependencyTreeButton = collapseTreeAction(dependencyTree)
      .asActionButton()
      .bindEnabled(showDependencyTreeProperty and dependencyLoadingProperty)

    val usagesTitle = label(usagesTitleProperty)
    val usagesTree = SimpleTree(usagesTreeModel)
      .apply { cellRenderer = UsagesRenderer() }
      .apply { emptyText.text = "" }
      .apply { expandAllWhenStructureChanged(this) }
      .bindEnabled(dependencyLoadingProperty)
    val expandUsagesTreeButton = expandTreeAction(usagesTree)
      .asActionButton()
      .bindEnabled(dependencyLoadingProperty)
    val collapseUsagesTreeButton = collapseTreeAction(usagesTree)
      .asActionButton()
      .bindEnabled(dependencyLoadingProperty)

    component = toolWindowPanel {
      toolbar = toolbarPanel {
        addToLeft(horizontalPanel(
          externalProjectSelector,
          dataFilterField,
          scopeFilterSelector,
          separator(),
          dependencyInspectionFilterButton,
          viewOptionsButton
        ))
      }
      setContent(horizontalSplitPanel(SPLIT_VIEW_PROPORTION_PROPERTY, 0.5f) {
        firstComponent = toolWindowPanel {
          toolbar = toolbarPanel {
            addToLeft(dependencyTitle)
            addToRight(horizontalPanel(
              showDependencyTreeButton,
              separator(),
              expandDependencyTreeButton,
              collapseDependencyTreeButton
            ))
          }
          setContent(ScrollPaneFactory.createScrollPane(dependencyLoadingPanel, true))
        }
        secondComponent = toolWindowPanel {
          toolbar = toolbarPanel {
            addToLeft(usagesTitle)
            addToRight(horizontalPanel(
              expandUsagesTreeButton,
              collapseUsagesTreeButton
            ))
          }
          setContent(ScrollPaneFactory.createScrollPane(usagesTree, true))
        }
      })
    }
  }

  init {
    externalProjectProperty.afterChange { updateScopesModel() }
    externalProjectProperty.afterChange { updateDependencyModel() }
    dependencyModelProperty.afterChange { updateFilteredDependencyModel() }
    dependencyDataFilterProperty.afterChange { updateFilteredDependencyModel() }
    dependencyScopeFilterProperty.afterChange { updateFilteredDependencyModel() }
    showDependencyWarningsProperty.afterChange { updateFilteredDependencyModel() }
    showDependencyGroupIdProperty.afterChange { updateFilteredDependencyModel() }
    dependencyProperty.afterChange { updateUsagesTitle() }
    dependencyGroupProperty.afterChange { updateUsagesModel() }
    showDependencyGroupIdProperty.afterChange { updateUsagesTitle() }
    dependencyLoadingProperty.afterChange { updateDependencyEmptyState() }
    contributor.whenDataChanged(::updateViewModel, parentDisposable)
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
    tree.whenStructureChanged {
      invokeLater {
        TreeUtil.expandAll(tree)
      }
    }
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

  private class DependencyModel(val dependencies: List<Dependency>) {
    val dependencyGroups: List<DependencyGroup>

    private val index = HashMap<Dependency, DependencyGroup>()

    private val Dependency.Data.group: String
      get() = when (this) {
        is Dependency.Data.Module -> name
        is Dependency.Data.Artifact -> "$groupId:$artifactId"
      }

    operator fun get(dependency: Dependency?): DependencyGroup? {
      if (dependency != null) {
        return index[dependency]
      }
      return null
    }

    init {
      dependencyGroups = dependencies
        .groupBy { it.data.group }
        .map { DependencyGroup(it.value) }
      for (group in dependencyGroups) {
        for (dependency in group.variances) {
          index[dependency] = group
        }
      }
    }

    companion object {
      val EMPTY = DependencyModel(emptyList())
    }
  }

  companion object {
    private val SEARCH_HISTORY_PROPERTY = DependencyAnalyzerView::class.java.name + ".search"
    private val SHOW_GROUP_ID_PROPERTY = DependencyAnalyzerView::class.java.name + ".showGroupId"
    private val SHOW_AS_TREE_PROPERTY = DependencyAnalyzerView::class.java.name + ".showAsTree"
    private val SPLIT_VIEW_PROPORTION_PROPERTY = DependencyAnalyzerView::class.java.name + ".splitProportion"
  }
}
