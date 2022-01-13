// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.List;

public interface BookmarksListProvider {
  ProjectExtensionPointName<BookmarksListProvider> EP = new ProjectExtensionPointName<>("com.intellij.bookmarksListProvider");

  int getWeight();

  @NotNull Project getProject();

  @Nullable AbstractTreeNode<?> createNode();

  @ApiStatus.Experimental
  default @Nullable OpenFileDescriptor getDescriptor(@NotNull AbstractTreeNode<?> node) {
    return null;
  }


  @Nullable @ActionText String getEditActionText();

  boolean canEdit(@NotNull Object selection);

  void performEdit(@NotNull Object selection, @NotNull JComponent parent);


  @Nullable @ActionText String getDeleteActionText();

  boolean canDelete(@NotNull List<?> selection);

  void performDelete(@NotNull List<?> selection, @NotNull JComponent parent);
}
