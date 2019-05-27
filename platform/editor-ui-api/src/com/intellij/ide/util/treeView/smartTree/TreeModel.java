// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView.smartTree;

import org.jetbrains.annotations.NotNull;

/**
 * Model for a generic tree control displayed in the IDE user interface, with a set
 * of actions for grouping, sorting, and filtering.
 * Used, for example, for the structure view tree.
 */
public interface TreeModel {

  /**
   * Returns the root element of the tree.
   *
   * @return the tree root.
   */
  @NotNull
  TreeElement getRoot();

  /**
   * Returns the list of actions for grouping items in the tree.
   *
   * @return the array of grouping actions.
   * @see Grouper#EMPTY_ARRAY
   */
  @NotNull
  Grouper[] getGroupers();

  /**
   * Returns the array of actions for sorting items in the tree.
   *
   * @return the array of sorting actions.
   * @see Sorter#EMPTY_ARRAY
   */
  @NotNull
  Sorter[] getSorters();

  /**
   * Returns the array of actions for filtering items in the tree.
   *
   * @return the array of filtering actions.
   * @see Filter#EMPTY_ARRAY
   */
  @NotNull
  Filter[] getFilters();
}
