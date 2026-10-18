// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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

  /** Returns the source value whose content represents this node when structures are compared. */
  @ApiStatus.Experimental
  default Object getValueForComparison() {
    return getValue();
  }

  /** Returns the text to show when structures are compared. */
  @ApiStatus.Experimental
  default @Nullable String getPresentableTextForComparison() {
    return getPresentation().getPresentableText();
  }
}
