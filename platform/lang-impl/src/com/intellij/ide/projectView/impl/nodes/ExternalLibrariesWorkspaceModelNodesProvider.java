// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
@ApiStatus.OverrideOnly
public interface ExternalLibrariesWorkspaceModelNodesProvider<T extends WorkspaceEntity> {
  ExtensionPointName<ExternalLibrariesWorkspaceModelNodesProvider<?>> EP =
    new ExtensionPointName<>("com.intellij.projectView.externalLibraries.workspaceModelNodesProvider");

  @NotNull
  Class<T> getWorkspaceClass();

  @Nullable
  AbstractTreeNode<?> createNode(@NotNull T entity, @NotNull Project project, ViewSettings settings);
}
