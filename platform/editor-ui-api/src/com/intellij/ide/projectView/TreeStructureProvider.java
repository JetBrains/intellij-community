// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Allows modifying the structure of a project as displayed in the project view.
 * <p/>
 * Can implement {@link com.intellij.openapi.project.DumbAware} additionally.
 *
 * @see ProjectViewNodeDecorator
 */
public interface TreeStructureProvider extends PossiblyDumbAware {

  /**
   * Allows modifying the list of children displayed for the specified node in the
   * project view.
   *
   * @param parent   the parent node.
   * @param children the list of child nodes according to the default project structure.
   *                 Elements of the collection are of type {@link ProjectViewNode}.
   * @param settings the current project view settings.
   * @return the modified collection of child nodes, or {@code children} if no modifications
   * are required.
   */
  @NotNull
  Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                         @NotNull Collection<AbstractTreeNode<?>> children,
                                         ViewSettings settings);

  /**
   * Override to populate UI data snapshot for the selection.
   * Or implement a {@link com.intellij.openapi.actionSystem.UiDataRule} instead.
   *
   * @see com.intellij.openapi.actionSystem.UiDataProvider
   */
  default void uiDataSnapshot(@NotNull DataSink sink,
                              @NotNull Collection<? extends AbstractTreeNode<?>> selection) {
    DataSink.uiDataSnapshot(sink, dataId -> getData(selection, dataId));
  }

  /**
   * Returns a user data object of the specified type for the specified selection in the
   * project view.
   *
   * @param selected the list of nodes currently selected in the project view.
   * @param dataId   the identifier of the requested data object (for example, as defined in
   *                 {@link com.intellij.openapi.actionSystem.PlatformDataKeys})
   * @return the data object, or null if no data object can be returned by this provider.
   * @see com.intellij.openapi.actionSystem.DataProvider
   *
   * @deprecated Use {@link #uiDataSnapshot(DataSink, Collection)} instead.
   */
  @Deprecated(forRemoval = true)
  default @Nullable Object getData(@NotNull Collection<? extends AbstractTreeNode<?>> selected, @NotNull String dataId) {
    return null;
  }

  @ApiStatus.Internal
  ProjectExtensionPointName<TreeStructureProvider> EP = new ProjectExtensionPointName<>("com.intellij.treeStructureProvider");
}
