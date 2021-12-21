// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerContributor.Dependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerContributor.Status
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.AtomicObservableProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.ui.asSequence
import com.intellij.openapi.ui.whenStructureChanged
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.lockOrSkip
import com.intellij.util.ui.tree.TreeUtil
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JList
import javax.swing.JTree
import javax.swing.ListModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel


private fun <T> ObservableMutableProperty<T>.bind(property: ObservableMutableProperty<T>) {
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      set(it)
    }
  }
  afterChange {
    mutex.lockOrSkip {
      property.set(it)
    }
  }
}

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
  private val dataProvider: DataProvider
) : JBList<DependencyGroup>(model), DataProvider {

  private val dependencyProperty = AtomicObservableProperty<Dependency?>(null)
  private val dependencyGroupProperty = AtomicObservableProperty<DependencyGroup?>(null)

  fun bindDependency(property: ObservableMutableProperty<Dependency?>) = apply {
    dependencyProperty.bind(property)
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DependencyAnalyzerView.DEPENDENCY.name -> dependencyProperty.get()
      DependencyAnalyzerView.DEPENDENCIES.name -> dependencyGroupProperty.get()
      else -> dataProvider.getData(dataId)
    }
  }

  init {
    bind(dependencyGroupProperty)
    dependencyGroupProperty.bind(
      dependencyProperty.transform(
        { dependency ->
          model.asSequence()
            .find { it.data == dependency?.data }
        },
        { it?.variances?.firstOrNull() }
      )
    )
  }
}

internal abstract class AbstractDependencyTree(
  model: TreeModel,
  private val dataProvider: DataProvider
) : SimpleTree(model), DataProvider {

  private val dependencyProperty = AtomicObservableProperty<Dependency?>(null)
  private val dependencyGroupProperty = AtomicObservableProperty<DependencyGroup?>(null)

  fun bindDependency(property: ObservableMutableProperty<Dependency?>) = apply {
    dependencyProperty.bind(property)
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      DependencyAnalyzerView.DEPENDENCY.name -> dependencyProperty.get()
      DependencyAnalyzerView.DEPENDENCIES.name -> dependencyGroupProperty.get()
      else -> dataProvider.getData(dataId)
    }
  }

  init {
    bind(dependencyGroupProperty)
    dependencyGroupProperty.bind(dependencyProperty.transform(
      { dependency ->
        model.asSequence()
          .map { it.userObject as DependencyGroup }
          .find { it.data == dependency?.data && dependency.parent in it.parents }
      },
      { it?.variances?.firstOrNull() }
    ))
  }
}

internal class DependencyList(
  model: ListModel<DependencyGroup>,
  showGroupIdProperty: ObservableProperty<Boolean>,
  dataProvider: DataProvider
) : AbstractDependencyList(model, dataProvider) {
  init {
    PopupHandler.installPopupMenu(this, "ExternalSystem.DependencyAnalyzer.DependencyListGroup", DependencyAnalyzerView.ACTION_PLACE)
    setCellRenderer(DependencyListRenderer(showGroupIdProperty))
  }
}

internal class DependencyTree(
  model: TreeModel,
  showGroupIdProperty: ObservableProperty<Boolean>,
  dataProvider: DataProvider
) : AbstractDependencyTree(model, dataProvider) {
  init {
    PopupHandler.installPopupMenu(this, "ExternalSystem.DependencyAnalyzer.DependencyTreeGroup", DependencyAnalyzerView.ACTION_PLACE)
    setCellRenderer(DependencyTreeRenderer(showGroupIdProperty))
  }
}

internal class UsagesTree(
  model: TreeModel,
  showGroupIdProperty: ObservableProperty<Boolean>,
  dataProvider: DataProvider
) : AbstractDependencyTree(model, dataProvider) {
  init {
    PopupHandler.installPopupMenu(this, "ExternalSystem.DependencyAnalyzer.UsagesTreeGroup", DependencyAnalyzerView.ACTION_PLACE)
    setCellRenderer(UsagesTreeRenderer(showGroupIdProperty))
    whenStructureChanged {
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
  val data by lazy { variances.first().data }
  val scopes by lazy { variances.map { it.scope }.toSet() }
  val parents by lazy { variances.map { it.parent }.toSet() }
  val warnings by lazy { variances.flatMap { it.status }.filterIsInstance<Status.Warning>() }
  val isOmitted by lazy { variances.all { Status.Omitted in it.status } }
  val hasWarnings by lazy { warnings.isNotEmpty() }

  override fun toString() = "s${scopes.size} v${variances.size} $data"
}
