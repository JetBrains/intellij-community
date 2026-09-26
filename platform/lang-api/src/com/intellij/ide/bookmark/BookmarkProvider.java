// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public interface BookmarkProvider extends Comparator<Bookmark> {
  ProjectExtensionPointName<BookmarkProvider> EP = new ProjectExtensionPointName<>("com.intellij.bookmarkProvider");

  int getWeight();

  @NotNull Project getProject();

  @Nullable Bookmark createBookmark(@NotNull Map<String, String> map);

  @Nullable Bookmark createBookmark(@Nullable Object context);

  default @NotNull List<AbstractTreeNode<?>> prepareGroup(@NotNull List<AbstractTreeNode<?>> nodes) {
    return nodes;
  }
}
