// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * Allows modifying the presentation of project view and package dependencies view nodes.
 *
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/project-view.html#decorating-project-view-nodes">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see TreeStructureProvider
 */
public interface ProjectViewNodeDecorator {
  ProjectExtensionPointName<ProjectViewNodeDecorator> EP = new ProjectExtensionPointName<>("com.intellij.projectViewNodeDecorator");

  /**
   * Modifies the presentation of a project view node.
   *
   * @param node the node to modify (use {@link ProjectViewNode#getValue()} to get the object represented by the node).
   * @param data the current presentation of the node, which you can modify as necessary.
   */
  void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data);

  /**
   * @deprecated This method is never called by the platform and should not be overridden.
   */
  @Deprecated(forRemoval = true)
  default void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
  }
}
