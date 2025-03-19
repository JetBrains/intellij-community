// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.*
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.observable.util.whenTreeChanged
import com.intellij.openapi.ui.asSequence
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JList
import javax.swing.JTree
import javax.swing.ListModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency


internal fun Dependency.Data.getDisplayText(showGroupId: Boolean): @NlsSafe String =
  when (this) {
    is Dependency.Data.Module -> name
    is Dependency.Data.Artifact -> when (showGroupId) {
      true -> "$groupId:$artifactId:$version"
      else -> "$artifactId:$version"
    }
  }

private fun SimpleColoredComponent.customizeCellRenderer(group: DependencyGroup, showGroupId: Boolean) {
  icon = when {
    group.hasWarnings -> AllIcons.General.Warning
    else -> when (group.data) {
      is Dependency.Data.Module -> AllIcons.Nodes.Module
      is Dependency.Data.Artifact -> AllIcons.Nodes.PpLib
    }
  }
  val dataText = group.data.getDisplayText(showGroupId)
  append(dataText, if (group.isOmitted) GRAYED_ATTRIBUTES else REGULAR_ATTRIBUTES)
  val scopes = group.variances.map { it.scope.name }.toSet()
  val scopesText = scopes.singleOrNull() ?: ExternalSystemBundle.message("external.system.dependency.analyzer.scope.n", scopes.size)
  append(" ($scopesText)", GRAYED_ATTRIBUTES)
}

internal abstract class AbstractDependencyList(
  model: ListModel<DependencyGroup>,
) : JBList<DependencyGroup>(model), UiDataProvider {

  private val dependencyProperty = AtomicProperty<Dependency?>(null)
  private val dependencyGroupProperty = AtomicProperty<DependencyGroup?>(null)

  fun bindDependency(property: ObservableMutableProperty<Dependency?>) = apply {
    dependencyProperty.bind(property)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DependencyAnalyzerView.DEPENDENCY] = dependencyProperty.get()
    sink[DependencyAnalyzerView.DEPENDENCIES] = dependencyGroupProperty.get()?.variances
  }

  init {
    bind(dependencyGroupProperty)
    dependencyGroupProperty.bind(
      dependencyProperty.transform(
        { dependency ->
          model.asSequence()
            .find { it.data == dependency?.data }
        },
        { it?.dependency }
      )
    )
  }
}

internal abstract class AbstractDependencyTree(
  model: TreeModel,
) : SimpleTree(model), UiDataProvider {

  private val dependencyProperty = AtomicProperty<Dependency?>(null)
  private val dependencyGroupProperty = AtomicProperty<DependencyGroup?>(null)

  fun bindDependency(property: ObservableMutableProperty<Dependency?>) = apply {
    dependencyProperty.bind(property)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DependencyAnalyzerView.DEPENDENCY] = dependencyProperty.get()
    sink[DependencyAnalyzerView.DEPENDENCIES] = dependencyGroupProperty.get()?.variances
  }

  init {
    bind(dependencyGroupProperty)
    dependencyGroupProperty.bind(dependencyProperty.transform(
      { dependency ->
        model.asSequence()
          .map { it.userObject as DependencyGroup }
          .find { it.data == dependency?.data && dependency.parent in it.parents }
      },
      { it?.dependency }
    ))
  }
}

internal open class DependencyList(
  model: ListModel<DependencyGroup>,
  showGroupIdProperty: ObservableProperty<Boolean>,
) : AbstractDependencyList(model) {
  init {
    ListUiUtil.Selection.installSelectionOnRightClick(this)
    PopupHandler.installPopupMenu(this, "ExternalSystem.DependencyAnalyzer.DependencyListGroup", DependencyAnalyzerView.ACTION_PLACE)
    setCellRenderer(DependencyListRenderer(showGroupIdProperty))
  }
}

internal open class DependencyTree(
  model: TreeModel,
  showGroupIdProperty: ObservableProperty<Boolean>,
) : AbstractDependencyTree(model) {
  init {
    PopupHandler.installPopupMenu(this, "ExternalSystem.DependencyAnalyzer.DependencyTreeGroup", DependencyAnalyzerView.ACTION_PLACE)
    setCellRenderer(DependencyTreeRenderer(showGroupIdProperty))
  }
}

internal open class UsagesTree(
  model: TreeModel,
  showGroupIdProperty: ObservableProperty<Boolean>,
) : AbstractDependencyTree(model) {
  init {
    PopupHandler.installPopupMenu(this, "ExternalSystem.DependencyAnalyzer.UsagesTreeGroup", DependencyAnalyzerView.ACTION_PLACE)
    setCellRenderer(UsagesTreeRenderer(showGroupIdProperty))
    whenTreeChanged {
      invokeLater {
        TreeUtil.expandAll(this)
      }
    }
  }
}

private class DependencyListRenderer(
  private val showGroupIdProperty: ObservableProperty<Boolean>
) : ColoredListCellRenderer<DependencyGroup>() {
  override fun customizeCellRenderer(
    list: JList<out DependencyGroup>,
    value: DependencyGroup?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean
  ) {
    val group = value ?: return
    customizeCellRenderer(group, showGroupIdProperty.get())
  }
}

private class DependencyTreeRenderer(private val showGroupIdProperty: ObservableProperty<Boolean>) : ColoredTreeCellRenderer() {
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
    val group = node.userObject as? DependencyGroup ?: return
    customizeCellRenderer(group, showGroupIdProperty.get())
  }
}

private class UsagesTreeRenderer(private val showGroupIdProperty: ObservableProperty<Boolean>) : ColoredTreeCellRenderer() {
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
    val group = node.userObject as? DependencyGroup ?: return
    customizeCellRenderer(group, showGroupIdProperty.get())
    val warning = group.warnings.firstOrNull()
    if (warning != null) {
      append(" ${warning.message}", SimpleTextAttributes.ERROR_ATTRIBUTES)
    }
  }
}

internal class DependencyGroup(val variances: List<Dependency>) {
  val dependency by lazy { variances.find { !it.isOmitted } ?: variances.first() }
  val data by lazy { dependency.data }
  val scopes by lazy { variances.map { it.scope }.toSet() }
  val parents by lazy { variances.map { it.parent }.toSet() }
  val warnings by lazy { variances.flatMap { it.warnings } }
  val isOmitted by lazy { variances.all { it.isOmitted } }
  val hasWarnings by lazy { variances.any { it.hasWarnings } }

  override fun toString() = data.toString()

  companion object {
    internal val Dependency.isOmitted: Boolean
      get() = status.any { it is Dependency.Status.Omitted }

    internal val Dependency.warnings: List<Dependency.Status.Warning>
      get() = status.filterIsInstance<Dependency.Status.Warning>()

    internal val Dependency.hasWarnings: Boolean
      get() = warnings.isNotEmpty()
  }
}
