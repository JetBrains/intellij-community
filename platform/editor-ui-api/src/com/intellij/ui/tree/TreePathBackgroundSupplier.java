// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import javax.swing.tree.TreePath;

public interface TreePathBackgroundSupplier {
  /**
   * This method allows to specify a background for a specified tree node.
   * Note, that this method is called from the Event Dispatch Thread during painting.
   *
   * @param path a path to a tree node in a tree
   * @param row  a visible index of a tree node that allows to define old fashioned striped background
   * @return a preferred background color or {@code null} if a tree node should use default background
   */
  @Nullable
  Color getPathBackground(@NotNull TreePath path, int row);
}
