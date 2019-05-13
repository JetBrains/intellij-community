/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * An element in a generic tree control displayed in the IDEA user interface.
 *
 * @see com.intellij.ide.util.treeView.smartTree.TreeModel#getRoot()
 */

public interface TreeElement {
  TreeElement[] EMPTY_ARRAY = new TreeElement[0];
  /**
   * Returns the presentation of the tree element.
   *
   * @return the element presentation.
   */
  @NotNull
  ItemPresentation getPresentation();

  /**
   * Returns the list of children of the tree element.
   *
   * @return the list of children.
   */
  @NotNull
  TreeElement[] getChildren();
}
