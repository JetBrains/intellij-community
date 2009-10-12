/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.treeView.smartTree;

/**
 * Action for filtering items in a generic tree.
 *
 * @see com.intellij.ide.util.treeView.smartTree.TreeModel#getFilters()
 */

public interface Filter extends TreeAction {
  Filter[] EMPTY_ARRAY = new Filter[0];

  /**
   * Checks if the specified tree element is accepted by the filter.
   *
   * @param treeNode the node to filter.
   * @return true if the node is visible, false otherwise.
   */
  boolean isVisible(TreeElement treeNode);

  /**
   * Checks if the filter is applied when the corresponding toolbar button is pressed or released.
   * For example, the "Show fields" filter works when the corresponding toolbar button is not pressed.
   *
   * @return false if the filter works when the button is pressed, true if the filter works when the
   * button is released.
   */
  boolean isReverted();
}
