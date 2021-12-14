// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Status
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.ObservableClearableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.ui.asSequence
import com.intellij.openapi.ui.whenStructureChanged
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.layout.*
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JList
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode


internal fun <C : JList<DependencyGroup>> C.bindDependency(property: ObservableClearableProperty<Dependency?>): C = apply {
  bind(
    property.transform(
      { dependency ->
        model.asSequence()
          .find { it.data == dependency?.data }
      },
      { it?.variances?.firstOrNull() }
    )
  )
}

internal fun <C : JTree> C.bindDependency(property: ObservableClearableProperty<Dependency?>): C = apply {
  bind(
    property.transform(
      { dependency ->
        model.asSequence()
          .map { it.userObject as DependencyGroup }
          .find { it.data == dependency?.data && dependency.parent in it.parents }
      },
      { it?.variances?.firstOrNull() }
    )
  )
}

internal fun expandAllWhenStructureChanged(tree: JTree) {
  tree.whenStructureChanged {
    invokeLater {
      TreeUtil.expandAll(tree)
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

internal class DependencyListRenderer(
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

internal class DependencyTreeRenderer(private val showGroupIdProperty: ObservableProperty<Boolean>) : ColoredTreeCellRenderer() {
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

internal class UsagesTreeRenderer(private val showGroupIdProperty: ObservableProperty<Boolean>) : ColoredTreeCellRenderer() {
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
