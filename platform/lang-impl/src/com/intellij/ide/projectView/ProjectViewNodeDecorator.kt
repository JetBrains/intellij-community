// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView

import com.intellij.packageDependencies.ui.PackageDependenciesNode
import com.intellij.ui.ColoredTreeCellRenderer

/**
 * Allows modifying the presentation of project view and package dependencies view nodes.
 *
 * Please see the [IntelliJ Platform Docs](https://plugins.jetbrains.com/docs/intellij/project-view.html#decorating-project-view-nodes)
 * for a high-level overview.
 *
 * @see TreeStructureProvider
 */
interface ProjectViewNodeDecorator {
  /**
   * Modifies the presentation of a project view node.
   *
   * @param node the node to modify (use [ProjectViewNode.getValue] to get the object represented by the node).
   * @param data the current presentation of the node, which you can modify as necessary.
   */
  fun decorate(node: ProjectViewNode<*>, data: PresentationData)

  @Deprecated("This method is never called by the platform and should not be overridden.")
  fun decorate(node: PackageDependenciesNode?, cellRenderer: ColoredTreeCellRenderer?) {
  }
}
