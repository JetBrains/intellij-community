// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
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
  private val dependencyProperty = propertyGraph.graphProperty<DependencyData?> { null }
  private val showGroupIdProperty = propertyGraph.graphProperty { false }

  private val usagesTitleProperty = propertyGraph.graphProperty(::getUsagesTitle)

  private val projectIdsModel = CollectionComboBoxModel<ExternalSystemProjectId>()
  private val scopesModel = CollectionComboBoxModel<Scope>()
  private val dependenciesModel = CollectionListModel<DependencyData>()
  private val usagesModel = CollectionListModel<DependencyData>()

  override fun setSelectedProjectId(projectId: ExternalSystemProjectId) {
    projectIdProperty.set(projectId)
  }

  override fun setSelectedDependency(projectId: ExternalSystemProjectId, dependency: DependencyData) {
    projectIdProperty.set(projectId)
    dependencyProperty.set(dependency)
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
      contributor.getDependencies(projectId)
        .forEach { dependenciesModel.add(it) }
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
      contributor.getUsages(projectId, dependency)
        .forEach { usagesModel.add(it) }
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
          setContent(dependenciesList(dependenciesModel) {
            bind(dependencyProperty)
            emptyText.text = ExternalSystemBundle.message("external.system.dependency.analyzer.resolved.empty")
          })
        },
        toolWindowPanel {
          toolbar = toolbarPanel {
            addOnLeft(JLabel())
              .bind(usagesTitleProperty)
              .apply { text = usagesTitleProperty.get() }
          }
          setContent(dependenciesList(usagesModel) {
            emptyText.text = ""
          })
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
    return when (val dependency = dependencyProperty.get()) {
      is DependencyData.Module -> {
        ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", dependency.moduleName)
      }
      is DependencyData.Artifact -> {
        val displayText = dependency.coordinate.getDisplayText()
        ExternalSystemBundle.message("external.system.dependency.analyzer.usages.title", displayText)
      }
      else -> ""
    }
  }

  private fun DependencyData.Artifact.Coordinates.getDisplayText(): @NlsSafe String {
    if (showGroupIdProperty.get()) {
      return "$groupId:$artifactId:$version"
    }
    else {
      return "$artifactId:$version"
    }
  }

  private fun dependenciesList(model: ListModel<DependencyData>, configure: JBList<DependencyData>.() -> Unit): JComponent {
    return ScrollPaneFactory.createScrollPane(JBList(model).apply {
      cellRenderer = DependencyRenderer()
      configure()
    })
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

  private inner class ProjectIdRenderer : SimpleListCellRenderer<ExternalSystemProjectId>() {
    override fun customize(list: JList<out ExternalSystemProjectId>,
                           value: ExternalSystemProjectId?,
                           index: Int,
                           selected: Boolean,
                           hasFocus: Boolean) {
      if (value == null) {
        text = ExternalSystemBundle.message("external.system.dependency.analyzer.projects.empty")
      }
      else {
        text = contributor.getProjectName(value)
      }
    }
  }

  private inner class DependencyRenderer : ColoredListCellRenderer<DependencyData>() {
    override fun customizeCellRenderer(list: JList<out DependencyData>,
                                       value: DependencyData?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      if (value is DependencyData.Module) {
        icon = AllIcons.Nodes.Module
        append(value.moduleName)
      }
      if (value is DependencyData.Artifact) {
        icon = AllIcons.Nodes.PpLib
        append(value.coordinate.getDisplayText())
        append(" (${value.scope})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

  private sealed class Scope(val name: String) {
    object Any : Scope(ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any"))
    class Just(name: String) : Scope(name)

    override fun toString() = name
  }
}