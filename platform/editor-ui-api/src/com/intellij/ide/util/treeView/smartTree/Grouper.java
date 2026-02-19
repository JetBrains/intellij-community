// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Action for grouping items in a generic tree.
 *
 * @see TreeModel#getGroupers()
 */

public interface Grouper extends TreeAction {
  Grouper[] EMPTY_ARRAY = new Grouper[0];

  /**
   * Returns the collection of groups into which the children of the specified parent node
   * are grouped.
   *
   * @param parent   the parent node.
   * @param children the children of the parent node.
   * @return the collection of groups
   */
  @NotNull
  @Unmodifiable
  Collection<Group> group(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<TreeElement> children);
}
