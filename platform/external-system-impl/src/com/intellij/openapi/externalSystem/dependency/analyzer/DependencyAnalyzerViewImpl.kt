// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware.Companion.isNotificationVisibleProperty
import com.intellij.openapi.externalSystem.autoimport.ProjectRefreshAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView.Companion.ACTION_PLACE
import com.intellij.openapi.externalSystem.dependency.analyzer.util.*
import com.intellij.openapi.externalSystem.dependency.analyzer.util.DependencyGroup.Companion.hasWarnings
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.getOperationInProgressProperty
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.withCompletedOperation
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addPreferredFocusedComponent
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.util.*
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

@ApiStatus.Internal
class DependencyAnalyzerViewImpl(
  private val project: Project,
  private val systemId: ProjectSystemId,
  private val parentDisposable: Disposable
) : DependencyAnalyzerView {

  private val iconsProvider = ExternalSystemIconProvider.getExtension(systemId)
  private val contributor = DependencyAnalyzerExtension.getExtension(systemId)
    .createContributor(project, parentDisposable)

  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("DependencyAnalyzerView.backgroundExecutor", 1)

  private val dependencyLoadingOperation = AtomicOperationTrace("DA: Dependency loading")
  private val dependencyLoadingProperty = dependencyLoadingOperation.getOperationInProgressProperty()

  private val externalProjectProperty = AtomicProperty<DependencyAnalyzerProject?>(null)
  private val dependencyDataFilterProperty = AtomicProperty("")
  private val dependencyScopeFilterProperty = AtomicProperty(emptyList<ScopeItem>())
  private val showDependencyWarningsProperty = AtomicProperty(false)
  private val showDependencyGroupIdProperty = AtomicProperty(false)
    .bindBooleanStorage(SHOW_GROUP_ID_PROPERTY)

  private val dependencyModelProperty = AtomicProperty(emptyList<DependencyGroup>())
  private val dependencyProperty = AtomicProperty<Dependency?>(null)
  private val showDependencyTreeProperty = AtomicProperty(false)
    .bindBooleanStorage(SHOW_AS_TREE_PROPERTY)
  private val dependencyEmptyTextProperty = AtomicProperty("")
  private val usagesTitleProperty = AtomicProperty("")

  private var externalProject by externalProjectProperty
  private var dependencyDataFilter by dependencyDataFilterProperty
  private var dependencyScopeFilter by dependencyScopeFilterProperty
  private var showDependencyWarnings by showDependencyWarningsProperty
  private var showDependencyGroupId by showDependencyGroupIdProperty

  private var dependencyModel by dependencyModelProperty
  private var dependency by dependencyProperty
  private var dependencyEmptyState by dependencyEmptyTextProperty
  private var usagesTitle by usagesTitleProperty

  private val externalProjects = ArrayList<DependencyAnalyzerProject>()

  private val dependencyListModel = CollectionListModel<DependencyGroup>()
  private val dependencyTreeModel = DefaultTreeModel(null)
  private val usagesTreeModel = DefaultTreeModel(null)

  override fun setSelectedExternalProject(module: Module) {
    setSelectedExternalProject(module) {}
  }

  override fun setSelectedDependency(module: Module, data: Dependency.Data) {
    setSelectedExternalProject(module) {
      dependency = findDependency { it.data == data } ?: dependency
    }
  }

  override fun setSelectedDependency(module: Module, data: Dependency.Data, scope: String) {
    setSelectedExternalProject(module) {
      dependency = findDependency { it.data == data && it.scope.name == scope } ?: dependency
    }
  }

  override fun setSelectedDependency(module: Module, path: List<DependencyAnalyzerDependency.Data>) {
    setSelectedExternalProject(module) {
      dependency = findDependency { d -> getTreePath(d).map { it.data } == path } ?: dependency
    }
  }

  override fun setSelectedDependency(module: Module, path: List<DependencyAnalyzerDependency.Data>, scope: String) {
    setSelectedExternalProject(module) {
      dependency = findDependency { d -> d.scope.name == scope && getTreePath(d).map { it.data } == path } ?: dependency
    }
  }

  private fun setSelectedExternalProject(module: Module, onReady: () -> Unit) {
    whenLoadingOperationCompleted {
      externalProject = findExternalProject { it.module == module } ?: externalProject
      whenLoadingOperationCompleted {
        onReady()
      }
    }
  }

  private fun findDependency(predicate: (Dependency) -> Boolean): Dependency? {
    return dependencyListModel.items.flatMap { it.variances }.find(predicate)
           ?: dependencyModel.flatMap { it.variances }.find(predicate)
  }

  private fun findExternalProject(predicate: (DependencyAnalyzerProject) -> Boolean): DependencyAnalyzerProject? {
    return externalProjects.find(predicate)
  }

  /**
   * Returns all resolved dependencies for the selected external project.
   */
  fun getDependencies(): List<Dependency> {
    return dependencyModel.flatMap { it.variances }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DependencyAnalyzerView.VIEW] = this
    sink[CommonDataKeys.PROJECT] = project
    sink[ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID] = systemId
    sink[PlatformCoreDataKeys.MODULE] = externalProject?.module
  }

  private fun updateViewModel() {
    executeLoadingTaskOnEdt {
      updateExternalProjectsModel()
    }
  }

  private fun Iterable<Dependency>.filterDependencies(): List<Dependency> {
    val dependencyDataFilter = dependencyDataFilter
    val dependencyScopeFilter = dependencyScopeFilter
      .filter { it.isSelected }
      .map { it.scope }
    val showDependencyWarnings = showDependencyWarnings
    return filter { dependency -> dependencyDataFilter.lowercase(Locale.ENGLISH) in dependency.data.getDisplayText(showDependencyGroupId).lowercase(Locale.ENGLISH) }
      .filter { dependency -> dependency.scope in dependencyScopeFilter }
      .filter { dependency -> if (showDependencyWarnings) dependency.hasWarnings else true }
  }

  private fun updateExternalProjectsModel() {
    externalProjects.clear()
    executeLoadingTask(
      onBackgroundThread = {
        contributor.getProjects()
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
        externalProject?.let(contributor::getDependencyScopes) ?: emptyList()
      },
      onUiThread = { scopes ->
        val scopesIndex = dependencyScopeFilter.associate { it.scope to it.isSelected }
        val isAny = scopesIndex.all { it.value }
        dependencyScopeFilter = scopes.map { ScopeItem(it, scopesIndex[it] ?: isAny) }
      }
    )
  }

  private fun updateDependencyModel() {
    dependencyModel = emptyList()
    executeLoadingTask(
      onBackgroundThread = {
        externalProject?.let(contributor::getDependencies) ?: emptyList()
      },
      onUiThread = { dependencies ->
        dependencyModel = dependencies
          .collectAllDependencies()
          .createDependencyGroups()
      }
    )
  }

  private fun updateFilteredDependencyModel() {
    val filteredDependencyGroups = dependencyModel.asSequence()
      .map { it.variances.filterDependencies() }
      .filter { it.isNotEmpty() }
      .map { DependencyGroup(it) }
      .toList()
    dependencyListModel.replaceAll(filteredDependencyGroups)

    val filteredDependencies = filteredDependencyGroups.flatMap { it.variances }
    dependencyTreeModel.setRoot(buildTree(filteredDependencies))

    dependency = filteredDependencies.find { it == dependency }
                 ?: filteredDependencies.firstOrNull()
  }

  private fun updateDependencyEmptyState() {
    dependencyEmptyState = when {
      dependencyLoadingOperation.isOperationInProgress() -> ""
      else -> ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty")
    }
  }

  private fun updateUsagesTitle() {
    val text = dependency?.data?.getDisplayText(showDependencyGroupId)
    usagesTitle = if (text == null) "" else ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", text)
  }

  private fun updateUsagesModel() {
    val dependencies = dependencyModel.asSequence()
      .filter { group -> dependency?.data in group.variances.map { it.data } }
      .flatMap { it.variances }
      .asIterable()
    usagesTreeModel.setRoot(buildTree(dependencies))
  }

  private fun executeLoadingTaskOnEdt(onUiThread: () -> Unit) {
    dependencyLoadingOperation.traceStart()
    runInEdt {
      onUiThread()
      dependencyLoadingOperation.traceFinish()
    }
  }

  private fun <R> executeLoadingTask(onBackgroundThread: () -> R, onUiThread: (R) -> Unit) {
    dependencyLoadingOperation.traceStart()
    BackgroundTaskUtil.execute(backgroundExecutor, parentDisposable) {
      val result = onBackgroundThread()
      invokeLater {
        onUiThread(result)
        dependencyLoadingOperation.traceFinish()
      }
    }
  }

  private fun whenLoadingOperationCompleted(onUiThread: () -> Unit) {
    dependencyLoadingOperation.withCompletedOperation(parentDisposable) {
      runInEdt {
        onUiThread()
      }
    }
  }

  private fun buildTree(dependencies: Iterable<Dependency>): DefaultMutableTreeNode? {
    val dependenciesForTree = dependencies.collectAllDependencies()

    if (dependenciesForTree.isEmpty()) {
      return null
    }
    val rootDependencyGroup = dependenciesForTree
      .filter { it.parent == null }
      .createDependencyGroups()
      .singleOrNull()
    if (rootDependencyGroup == null) {
      val rawTree = dependenciesForTree.joinToString("\n")
      logger<DependencyAnalyzerView>().error("Cannot determine root of dependency tree:\n$rawTree")
      return null
    }

    val nodeMap = LinkedHashMap<Dependency, MutableList<Dependency>>()
    for (dependency in dependenciesForTree) {
      val usage = dependency.parent ?: continue
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

  private fun Iterable<Dependency>.collectAllDependencies(): Set<Dependency> {
    return flatMap { getTreePath(it) }.toSet()
  }

  private fun getTreePath(dependency: Dependency): List<Dependency> {
    val dependencyPath = ArrayList<Dependency>()
    var current: Dependency? = dependency
    while (current != null) {
      dependencyPath.add(current)
      current = current.parent
    }
    return dependencyPath
  }

  private fun Dependency.Data.getGroup(): String = when (this) {
    is Dependency.Data.Module -> name
    is Dependency.Data.Artifact -> "$groupId:$artifactId"
  }

  private fun Iterable<Dependency>.createDependencyGroups(): List<DependencyGroup> =
    sortedWith(Comparator.comparing({ it.data }, DependencyDataComparator(showDependencyGroupId)))
      .groupBy { it.data.getGroup() }
      .map { DependencyGroup(it.value) }

  fun createComponent(): JComponent {
    val externalProjectSelector = ExternalProjectSelector(externalProjectProperty, externalProjects, iconsProvider)
      .bindEnabled(!dependencyLoadingProperty)
    val dataFilterField = SearchTextField(SEARCH_HISTORY_PROPERTY)
      .apply { setPreferredWidth(JBUI.scale(240)) }
      .apply { textEditor.bind(dependencyDataFilterProperty) }
      .bindEnabled(!dependencyLoadingProperty)
    val scopeFilterSelector = SearchScopeSelector(dependencyScopeFilterProperty)
      .bindEnabled(!dependencyLoadingProperty)
    val dependencyInspectionFilterButton = toggleAction(showDependencyWarningsProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.conflicts.show") }
      .apply { templatePresentation.icon = AllIcons.General.ShowWarning }
      .asActionButton(ACTION_PLACE)
      .bindEnabled(!dependencyLoadingProperty)
    val showDependencyGroupIdAction = toggleAction(showDependencyGroupIdProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.groupId.show") }
    val viewOptionsButton = popupActionGroup(showDependencyGroupIdAction)
      .apply { templatePresentation.icon = AllIcons.Actions.Show }
      .asActionButton(ACTION_PLACE)
      .bindEnabled(!dependencyLoadingProperty)
    val reloadNotificationProperty = isNotificationVisibleProperty(project, systemId)
    val projectReloadSeparator = separator()
      .bindVisible(reloadNotificationProperty)
    val projectReloadAction = action { ProjectRefreshAction.Manager.refreshProject(project) }
      .apply { templatePresentation.icon = AllIcons.Actions.BuildLoadChanges }
      .asActionButton(ACTION_PLACE)
      .bindVisible(reloadNotificationProperty)

    val dependencyTitle = label(ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.title"))
    val dependencyList = object : DependencyList(dependencyListModel, showDependencyGroupIdProperty) {
      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        DataSink.uiDataSnapshot(sink, this@DependencyAnalyzerViewImpl)
      }
    }
      .bindEmptyText(dependencyEmptyTextProperty)
      .bindDependency(dependencyProperty)
      .bindEnabled(!dependencyLoadingProperty)
    val dependencyTree = object : DependencyTree(dependencyTreeModel, showDependencyGroupIdProperty) {
      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        DataSink.uiDataSnapshot(sink, this@DependencyAnalyzerViewImpl)
      }
    }
      .bindEmptyText(dependencyEmptyTextProperty)
      .bindDependency(dependencyProperty)
      .bindEnabled(!dependencyLoadingProperty)
    val dependencyPanel = cardPanel<Boolean> { ScrollPaneFactory.createScrollPane(if (it) dependencyTree else dependencyList, true) }
      .bind(showDependencyTreeProperty)
    val dependencyLoadingPanel = JBLoadingPanel(BorderLayout(), parentDisposable)
      .apply { add(dependencyPanel, BorderLayout.CENTER) }
      .apply { setLoadingText(ExternalSystemBundle.message("external.system.dependency.analyzer.dependency.loading")) }
      .bind(dependencyLoadingProperty)
    val showDependencyTreeButton = toggleAction(showDependencyTreeProperty)
      .apply { templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.tree.show") }
      .apply { templatePresentation.icon = AllIcons.Actions.ShowAsTree }
      .asActionButton(ACTION_PLACE)
      .bindEnabled(!dependencyLoadingProperty)
    val expandDependencyTreeButton = expandTreeAction(dependencyTree)
      .asActionButton(ACTION_PLACE)
      .bindEnabled(showDependencyTreeProperty and !dependencyLoadingProperty)
    val collapseDependencyTreeButton = collapseTreeAction(dependencyTree)
      .asActionButton(ACTION_PLACE)
      .bindEnabled(showDependencyTreeProperty and !dependencyLoadingProperty)

    val usagesTitle = label(usagesTitleProperty)
    val usagesTree = object : UsagesTree(usagesTreeModel, showDependencyGroupIdProperty) {
      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        DataSink.uiDataSnapshot(sink, this@DependencyAnalyzerViewImpl)
      }
    }
      .apply { emptyText.text = "" }
      .bindEnabled(!dependencyLoadingProperty)
    val expandUsagesTreeButton = expandTreeAction(usagesTree)
      .asActionButton(ACTION_PLACE)
      .bindEnabled(!dependencyLoadingProperty)
    val collapseUsagesTreeButton = collapseTreeAction(usagesTree)
      .asActionButton(ACTION_PLACE)
      .bindEnabled(!dependencyLoadingProperty)

    return toolWindowPanel {
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
          setContent(dependencyLoadingPanel)
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
    }.also {
      it.addPreferredFocusedComponent(dataFilterField)
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

  private class DependencyDataComparator(private val showDependencyGroupId: Boolean) : Comparator<Dependency.Data> {
    override fun compare(o1: Dependency.Data, o2: Dependency.Data): Int {
      val text1 = o1.getDisplayText(showDependencyGroupId)
      val text2 = o2.getDisplayText(showDependencyGroupId)
      return when (o1) {
        is Dependency.Data.Module -> when (o2) {
          is Dependency.Data.Module -> NaturalComparator.INSTANCE.compare(text1, text2)
          is Dependency.Data.Artifact -> -1
        }

        is Dependency.Data.Artifact -> when (o2) {
          is Dependency.Data.Module -> 1
          is Dependency.Data.Artifact -> NaturalComparator.INSTANCE.compare(text1, text2)
        }
      }
    }
  }

  companion object {
    private const val SEARCH_HISTORY_PROPERTY = "ExternalSystem.DependencyAnalyzerView.search"
    private const val SHOW_GROUP_ID_PROPERTY = "ExternalSystem.DependencyAnalyzerView.showGroupId"
    private const val SHOW_AS_TREE_PROPERTY = "ExternalSystem.DependencyAnalyzerView.showAsTree"
    private const val SPLIT_VIEW_PROPORTION_PROPERTY = "ExternalSystem.DependencyAnalyzerView.splitProportion"
  }
}
