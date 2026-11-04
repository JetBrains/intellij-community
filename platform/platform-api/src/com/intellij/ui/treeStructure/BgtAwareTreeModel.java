// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Marker interface to indicate that the model can load nodes in the background without freezing the EDT
 */
public interface BgtAwareTreeModel extends TreeModel {
  /**
   * Starts to load the children of the node at the given path, if they are not loaded yet.
   * <p>
   * The method does not wait for the children.
   * A caller can call it often, so a call for a loaded or loading node must be cheap.
   * The default implementation calls {@link #getChildCount}, because a model like {@code AsyncTreeModel} starts the load there.
   */
  @ApiStatus.Experimental
  default void loadChildren(@NotNull TreePath path) {
    getChildCount(path.getLastPathComponent());
  }
}
