// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;

public final class CachingTreePath extends TreePath {
  private final int myPathCount;

  public CachingTreePath(@NotNull Object lastPathComponent) {
    this(null, lastPathComponent);
  }

  /**
   * @implSpec we are pre-caching PathCount, because our #{@link getPathCount} is recursive and may cause {@link StackOverflowError} when
   * deep tree is built first, and then, we are trying to call {@link #getPathCount()}
   */
  public CachingTreePath(@Nullable TreePath parent, @NotNull Object lastPathComponent) {
    super(parent, lastPathComponent);
    myPathCount = parent == null ? 1 : parent.getPathCount() + 1;
  }

  public CachingTreePath(Object @NotNull [] path) {
    this(path, path.length);
  }

  private CachingTreePath(Object @NotNull [] path, int length) {
    this(length == 1 ? null : new CachingTreePath(path, length - 1), path[length - 1]);
  }

  @Override
  public TreePath pathByAddingChild(@NotNull Object child) {
    return new CachingTreePath(this, child);
  }

  @Override
  public int getPathCount() {
    return myPathCount;
  }

  static @Nullable CachingTreePath ensureCaching(@Nullable TreePath path) {
    if (path == null) return null;
    if (path instanceof CachingTreePath cachingTreePath) return cachingTreePath;
    return new CachingTreePath(ensureCaching(path.getParentPath()), path.getLastPathComponent());
  }
}
