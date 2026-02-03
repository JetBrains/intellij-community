// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * An element in a generic tree control displayed in the IDE user interface.
 *
 * @see TreeModel#getRoot()
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
   * Returns the array of children of the tree element.
   *
   * @return the array of children.
   */
  TreeElement @NotNull [] getChildren();
}
