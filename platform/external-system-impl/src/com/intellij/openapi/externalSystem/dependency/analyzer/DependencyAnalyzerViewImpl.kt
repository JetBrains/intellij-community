// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.ProjectRefreshAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.*
import com.intellij.openapi.externalSystem.dependency.analyzer.util.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.asProperty
import com.intellij.openapi.observable.properties.AtomicObservableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.and
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.asSequence
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.layout.*
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

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

  private val externalProjectProperty = AtomicObservableProperty<ExternalProject?>(null)
  private val dependencyDataFilterProperty = AtomicObservableProperty("")
  private val dependencyScopeFilterProperty = AtomicObservableProperty(emptyList<ScopeItem>())
  private val showDependencyWarningsProperty = AtomicObservableProperty(false)
  private val showDependencyGroupIdProperty = AtomicObservableProperty(false)
    .bindWithBooleanStorage(SHOW_GROUP_ID_PROPERTY)

  private val dependencyModelProperty = AtomicObservableProperty(emptyList<Dependency>())
  private val dependencyProperty = AtomicObservableProperty<Dependency?>(null)
  private val showDependencyTreeProperty = AtomicObservableProperty(false)
    .bindWithBooleanStorage(SHOW_AS_TREE_PROPERTY)
  private val dependencyEmptyTextProperty = AtomicObservableProperty("")
  private val usagesTitleProperty = AtomicObservableProperty("")

  private var externalProject by externalProjectProperty
  private var dependencyDataFilter by dependencyDataFilterProperty
  private var dependencyScopeFilter by dependencyScopeFilterProperty
  private var showDependencyWarnings by showDependencyWarningsProperty
  private var showDependencyGroupId by showDependencyGroupIdProperty

  private var dependencyModel by dependencyModelProperty
  private var dependency by dependencyProperty
  private var dependencyEmptyState by dependencyEmptyTextProperty
  private var usagesTitle by usagesTitleProperty

  private val externalProjects = ArrayList<ExternalProject>()

  private val dependencyListModel = CollectionListModel<DependencyGroup>()
  private val dependencyTreeModel = DefaultTreeModel(null)
  private val usagesTreeModel = DefaultTreeModel(null)

  override fun setSelectedExternalProject(externalProjectPath: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    externalProject = externalProjects.find { it.path == externalProjectPath }
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

  private fun Iterable<Dependency>.filterDependencies(): List<Dependency> {
    val dependencyDataFilter = dependencyDataFilter
    val dependencyScopeFilter = dependencyScopeFilter
      .filter { it.isSelected }
      .map { it.scope }
    val showDependencyWarnings = showDependencyWarnings
    return filter { dependency -> dependencyDataFilter in dependency.data.getDisplayText(showDependencyGroupId) }
      .filter { dependency -> dependency.scope in dependencyScopeFilter }
      .filter { dependency -> if (showDependencyWarnings) dependency.status.any { it is Status.Warning } else true }
  }

  private fun updateExternalProjectsModel() {
    externalProjects.clear()
    executeLoadingTask(
      onBackgroundThread = {
        contributor.getExternalProjects()
      },
      onUiThread = { projects ->
        externalProjects.addAll(projects)

        externalProject = externalProjects.find { it == externalProject }
                          ?: externalProjects.firstOrNull()
      }
    )
  }

  private fun updateScopesModel() {
    executeLoadingTask(
      onBackgroundThread = {
        externalProject?.path?.let(contributor::getDependencyScopes) ?: emptyList()
      },
      onUiThread = { scopes ->
        val scopesIndex = dependencyScopeFilter.associate { it.scope.id to it.isSelected }
        val isAny = scopesIndex.all { it.value }
        dependencyScopeFilter = scopes.map { ScopeItem(it, scopesIndex[it.id] ?: isAny) }
      }
    )
  }

  private fun updateDependencyModel() {
    dependencyModel = emptyList()
    executeLoadingTask(
      onBackgroundThread = {
        externalProject?.path?.let(contributor::getDependencies) ?: emptyList()
      },
      onUiThread = {
        dependencyModel = it
      }
    )
  }

  private fun updateFilteredDependencyModel() {
    val filteredDependencyGroups = dependencyModel
      .filterDependencies()
      .createDependencyGroups()
    dependencyListModel.replaceAll(filteredDependencyGroups)

    val filteredDependencies = filteredDependencyGroups.flatMap { it.variances }
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
    val text = dependency?.data?.getDisplayText(showDependencyGroupId)
    usagesTitle = if (text == null) "" else ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", text)
  }

  private fun updateUsagesModel() {
    val dependencies = dependencyListModel.asSequence()
      .filter { it.data == dependency?.data }
      .flatMap { it.variances }
      .asIterable()
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

  private fun buildTree(dependencies: Iterable<Dependency>): DefaultMutableTreeNode? {
    val dependenciesForTree = dependencies.flatMap { getTreePath(it) }.toSet()

    if (dependenciesForTree.isEmpty()) {
      return null
    }
    val rootDependencyGroup = dependenciesForTree
      .filter { it.usage == null }
      .createDependencyGroups()
      .singleOrNull()
    if (rootDependencyGroup == null) {
      val rawTree = dependenciesForTree.joinToString("\n")
      logger<DependencyAnalyzerView>().error("Cannot determine root of dependency tree:\n$rawTree")
      return null
    }

    val nodeMap = LinkedHashMap<Dependency, MutableList<Dependency>>()
    for (dependency in dependenciesForTree) {
      val usage = dependency.usage ?: continue
      val children = nodeMap.getOrPut(usage) { ArrayList() }
      children.add(dependency)
    }

    val rootNode = DefaultMutableTreeNode(rootDependencyGroup)
    val queue = ArrayDeque<DefaultMutableTreeNode>()
    queue.addLast(rootNode)
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val dependencyGroup = node.userObject as DependencyGroup
      val children = dependencyGroup.variances
        .flatMap { nodeMap[it] ?: emptyList() }
        .createDependencyGroups()
      for (child in children) {
        val childNode = DefaultMutableTreeNode(child)
        node.add(childNode)
        queue.addLast(childNode)
      }
    }
    return rootNode
  }

  private fun getTreePath(dependency: Dependency): List<Dependency> {
    val dependencyPath = ArrayList<Dependency>()
    var current: Dependency? = dependency
    while (current != null) {
      dependencyPath.add(current)
      current = current.usage
    }
    return dependencyPath
  }

  private fun Dependency.Data.getGroup(): String = when (this) {
    is Dependency.Data.Module -> name
    is Dependency.Data.Artifact -> "$groupId:$artifactId"
  }

  private fun Iterable<Dependency>.createDependencyGroups(): List<DependencyGroup> =
    groupBy { it.data.getGroup() }
      .map { DependencyGroup(it.value) }

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
    val reloadNotificationProperty = ProjectReloadNotificationProperty()
    val projectReloadSeparator = separator()
      .bindVisible(reloadNotificationProperty)
    val projectReloadAction = action { ProjectRefreshAction.refreshProject(project) }
      .apply { templatePresentation.icon = AllIcons.Actions.BuildLoadChanges }
      .asActionButton()
      .bindVisible(reloadNotificationProperty)

    val dependencyTitle = label(ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.title"))
    val dependencyList = JBList(dependencyListModel)
      .apply { cellRenderer = DependencyListRenderer(showDependencyGroupIdProperty) }
      .bindEmptyText(dependencyEmptyTextProperty)
      .bindDependency(dependencyProperty)
      .bindEnabled(dependencyLoadingProperty)
    val dependencyTree = SimpleTree(dependencyTreeModel)
      .apply { cellRenderer = DependencyTreeRenderer(showDependencyGroupIdProperty) }
      .bindEmptyText(dependencyEmptyTextProperty)
      .bindDependency(dependencyProperty)
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
      .apply { cellRenderer = UsagesTreeRenderer(showDependencyGroupIdProperty) }
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
          viewOptionsButton,
          projectReloadSeparator,
          projectReloadAction
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
    dependencyProperty.afterChange { updateUsagesModel() }
    showDependencyGroupIdProperty.afterChange { updateUsagesTitle() }
    dependencyLoadingProperty.afterChange { updateDependencyEmptyState() }
    contributor.whenDataChanged(::updateViewModel, parentDisposable)
    updateViewModel()
  }

  private inner class ProjectReloadNotificationProperty : ObservableProperty<Boolean> {
    private val notificationAware get() = ExternalSystemProjectNotificationAware.getInstance(project)

    override fun get() = systemId in notificationAware.getSystemIds()

    override fun afterChange(listener: (Boolean) -> Unit) =
      ExternalSystemProjectNotificationAware.whenNotificationChanged(project) {
        listener(get())
      }

    override fun afterChange(listener: (Boolean) -> Unit, parentDisposable: Disposable) =
      ExternalSystemProjectNotificationAware.whenNotificationChanged(project, {
        listener(get())
      }, parentDisposable)
  }

  companion object {
    private val SEARCH_HISTORY_PROPERTY = DependencyAnalyzerView::class.java.name + ".search"
    private val SHOW_GROUP_ID_PROPERTY = DependencyAnalyzerView::class.java.name + ".showGroupId"
    private val SHOW_AS_TREE_PROPERTY = DependencyAnalyzerView::class.java.name + ".showAsTree"
    private val SPLIT_VIEW_PROPORTION_PROPERTY = DependencyAnalyzerView::class.java.name + ".splitProportion"
  }
}
