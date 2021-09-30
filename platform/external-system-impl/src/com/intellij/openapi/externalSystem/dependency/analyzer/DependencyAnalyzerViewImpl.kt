// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.Dependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.InspectionResult.Omitted
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.InspectionResult.VersionConflict
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

class DependencyAnalyzerViewImpl(private val contributor: DependenciesContributor) : DependencyAnalyzerView {
  override val component: JComponent

  private val propertyGraph = PropertyGraph()

  private val projectIdProperty = propertyGraph.graphProperty<ExternalSystemProjectId?> { null }
  private val searchRequestProperty = propertyGraph.graphProperty { "" }
  private val searchScopeProperty = propertyGraph.graphProperty<Scope> { Scope.Any }
  private val dependencyProperty = propertyGraph.graphProperty<DependencyItem?> { null }
  private val showGroupIdProperty = propertyGraph.graphProperty { false }

  private val usagesTitleProperty = propertyGraph.graphProperty(::getUsagesTitle)

  private val projectIdsModel = CollectionComboBoxModel<ExternalSystemProjectId>()
  private val scopesModel = CollectionComboBoxModel<Scope>()
  private val dependenciesModel = CollectionListModel<DependencyItem>()
  private val usagesModel = CollectionListModel<DependencyItem>()

  override fun setSelectedProjectId(projectId: ExternalSystemProjectId) {
    projectIdProperty.set(projectId)
  }

  override fun setSelectedDependency(projectId: ExternalSystemProjectId, dependency: Dependency) {
    projectIdProperty.set(projectId)
    dependencyProperty.set(DependencyItem(projectId, dependency))
  }

  private fun updateProjectIdsModel() {
    projectIdsModel.removeAll()
    contributor.getProjectIds()
      .forEach { projectIdsModel.add(it) }

    val projectId = projectIdProperty.get()
    if (projectId == null || projectId !in projectIdsModel) {
      projectIdProperty.set(projectIdsModel.items.firstOrNull())
    }
  }

  private fun updateScopesModel() {
    scopesModel.removeAll()
    scopesModel.add(Scope.Any)
    val projectId = projectIdProperty.get()
    if (projectId != null) {
      contributor.getDependencyScopes(projectId)
        .forEach { scopesModel.add(Scope.Just(it)) }
    }

    val scope = searchScopeProperty.get()
    if (scope !in scopesModel) {
      searchScopeProperty.set(Scope.Any)
    }
  }

  private fun updateDependenciesModel() {
    dependenciesModel.removeAll()
    val projectId = projectIdProperty.get()
    if (projectId != null) {
      val queue = ArrayDeque<DependencyItem>()
      queue.addLast(DependencyItem(projectId, contributor.getRoot(projectId)))
      while (queue.isNotEmpty()) {
        val dependency = queue.removeFirst()
        dependenciesModel.add(dependency)
        dependency.dependencies.forEach {
          queue.addLast(DependencyItem(projectId, it))
        }
      }
    }

    val dependency = dependencyProperty.get()
    if (dependency == null || dependency !in dependenciesModel) {
      dependencyProperty.set(dependenciesModel.items.firstOrNull())
    }
  }

  private fun updateUsagesModel() {
    usagesModel.removeAll()
    val projectId = projectIdProperty.get()
    val dependency = dependencyProperty.get()
    if (projectId != null && dependency != null) {
      for (candidate in dependency.variances) {
        var current: Dependency? = candidate
        while (current != null) {
          usagesModel.add(DependencyItem(projectId, current))
          current = current.usage
        }
      }
    }
  }

  private fun horizontalFlow(vararg components: JComponent): JComponent {
    return JPanel().apply {
      layout = GridLayout(1, components.size)
      components.forEach(::add)
    }
  }

  init {
    scopesModel.add(Scope.Any)
    component = toolWindowPanel {
      toolbar = toolbarPanel {
        addOnLeft(horizontalFlow(
          ComboBox(projectIdsModel)
            .bind(projectIdProperty)
            .apply { selectedItem = projectIdProperty.get() }
            .apply { renderer = ProjectIdRenderer() },
          JTextField()
            .bind(searchRequestProperty)
            .apply { text = searchRequestProperty.get() },
          ExternalSystemBundle.message("external.system.dependency.analyzer.scope.label")
            .labelFor(
              ComboBox(scopesModel)
                .bind(searchScopeProperty)
                .apply { selectedItem = searchScopeProperty.get() }
            )
        ))
      }
      setContent(horizontalFlow(
        toolWindowPanel {
          toolbar = toolbarPanel {
            addOnLeft(JLabel(ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.title")))
          }
          setContent(ScrollPaneFactory.createScrollPane(JBList(dependenciesModel).apply {
            cellRenderer = DependenciesRenderer()
            bind(dependencyProperty)
            emptyText.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty")
          }))
        },
        toolWindowPanel {
          toolbar = toolbarPanel {
            addOnLeft(JLabel())
              .bind(usagesTitleProperty)
              .apply { text = usagesTitleProperty.get() }
          }
          setContent(ScrollPaneFactory.createScrollPane(JBList(usagesModel).apply {
            cellRenderer = UsagesRenderer()
            emptyText.text = ""
          }))
        }
      ))
    }
  }

  init {
    projectIdProperty.afterChange {
      updateScopesModel()
      updateDependenciesModel()
    }
    dependencyProperty.afterChange {
      updateUsagesModel()
    }
    updateProjectIdsModel()

    usagesTitleProperty.dependsOn(dependencyProperty)
    usagesTitleProperty.dependsOn(showGroupIdProperty)
  }

  private fun getUsagesTitle(): @NlsContexts.Label String {
    val dependency = dependencyProperty.get() ?: return ""
    return ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", dependency.displayText)
  }

  private fun <C : JComponent> JPanel.addOnLeft(component: C): C {
    add(component, BorderLayout.WEST)
    return component
  }

  private fun <C : JComponent> @NlsContexts.Label String.labelFor(component: C): LabeledComponent<C> {
    return LabeledComponent.create(component, this, BorderLayout.WEST)
  }

  private fun toolWindowPanel(configure: SimpleToolWindowPanel.() -> Unit) =
    SimpleToolWindowPanel(true, true)
      .apply { configure() }

  private fun toolbarPanel(configure: BorderLayoutPanel.() -> Unit) =
    BorderLayoutPanel()
      .apply { border = JBUI.Borders.empty() }
      .apply { withMinimumHeight(JBUI.scale(30)) }
      .apply { withPreferredHeight(JBUI.scale(30)) }
      .apply { configure() }

  private val Dependency.Data.displayText: @NlsSafe String
    get() = when (this) {
      is Dependency.Data.Module -> name
      is Dependency.Data.Artifact ->
        if (showGroupIdProperty.get()) {
          "$groupId:$artifactId:$version"
        }
        else {
          "$artifactId:$version"
        }
    }

  private inner class ProjectIdRenderer : SimpleListCellRenderer<ExternalSystemProjectId>() {
    override fun customize(
      list: JList<out ExternalSystemProjectId>,
      value: ExternalSystemProjectId?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      if (value == null) {
        text = ExternalSystemBundle.message("external.system.dependency.analyzer.projects.empty")
      }
      else {
        text = contributor.getProjectName(value)
      }
    }
  }

  private abstract inner class AbstractDependencyRenderer : ColoredListCellRenderer<DependencyItem>() {
    open fun setupIcon(projectId: ExternalSystemProjectId, dependency: DependencyItem) {
      icon = when (dependency.dependency.data) {
        is Dependency.Data.Module -> AllIcons.Nodes.Module
        is Dependency.Data.Artifact -> AllIcons.Nodes.PpLib
      }
    }

    open fun setupName(projectId: ExternalSystemProjectId, dependency: DependencyItem) {
      if (Omitted in dependency.inspectionResult) {
        append(dependency.displayText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      else {
        append(dependency.displayText)
      }
    }

    open fun setupScope(projectId: ExternalSystemProjectId, dependency: DependencyItem) {
      if (Omitted !in dependency.inspectionResult) {
        append(" (${dependency.scope})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }

    override fun customizeCellRenderer(
      list: JList<out DependencyItem>,
      value: DependencyItem?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      val projectId = projectIdProperty.get() ?: return
      val dependency = value ?: return
      setupIcon(projectId, dependency)
      setupName(projectId, dependency)
      setupScope(projectId, dependency)
    }
  }

  private inner class DependenciesRenderer : AbstractDependencyRenderer() {
    override fun setupIcon(projectId: ExternalSystemProjectId, dependency: DependencyItem) {
      if (dependency.inspectionResult.any { it is VersionConflict }) {
        icon = AllIcons.General.Warning
      }
      else {
        super.setupIcon(projectId, dependency)
      }
    }
  }

  private inner class UsagesRenderer : AbstractDependencyRenderer() {
    override fun setupScope(projectId: ExternalSystemProjectId, dependency: DependencyItem) {
      super.setupScope(projectId, dependency)
      val versionConflict = dependency.inspectionResult.filterIsInstance<VersionConflict>().firstOrNull()
      if (versionConflict != null) {
        val version = versionConflict.conflicted.version
        val message = ExternalSystemBundle.message("external.system.dependency.analyzer.error.version.conflict", version)
        append(" $message", SimpleTextAttributes.ERROR_ATTRIBUTES)
      }
    }
  }

  private sealed class Scope(val name: String) {
    object Any : Scope(ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any"))
    class Just(name: String) : Scope(name)

    override fun toString() = name
  }

  private inner class DependencyItem(val projectId: ExternalSystemProjectId, val dependency: Dependency) {
    val scope by lazy { contributor.getDependencyScope(projectId, dependency) }
    val dependencies by lazy { contributor.getDependencies(projectId, dependency) }
    val variances by lazy { contributor.getVariances(projectId, dependency) }
    val inspectionResult by lazy { contributor.getInspectionResult(projectId, dependency) }
    val displayText by lazy { dependency.data.displayText }
  }
}
