// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.pom.Navigatable;

/**
 * An element in the structure view tree model.
 *
 * @see StructureViewModel#getRoot()
 */

public interface StructureViewTreeElement extends TreeElement, Navigatable {
  StructureViewTreeElement[] EMPTY_ARRAY = new StructureViewTreeElement[0];

  /**
   * Returns the data object (usually a PSI element) corresponding to the
   * structure view element.
   *
   * @return the data object instance.
   */
  Object getValue();
}
