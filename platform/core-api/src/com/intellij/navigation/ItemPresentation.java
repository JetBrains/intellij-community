// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The presentation of an item in a tree, list or similar view.
 *
 * @see com.intellij.ide.util.treeView.smartTree.TreeElement#getPresentation()
 * @see com.intellij.ide.projectView.PresentationData
 */

public interface ItemPresentation {
  /**
   * Returns the name of the object to be presented in most renderers across the program.
   *
   * @return the object name.
   */
  @NlsSafe @Nullable String getPresentableText();

  /**
   * Returns the location of the object (for example, the package of a class). The location
   * string is used by some renderers and usually displayed as grayed text next to the item name.
   *
   * @return the location description, or null if none is applicable.
   */
  default @NlsSafe @Nullable String getLocationString() {
    return null;
  }

  /**
   * Returns the icon representing the object.
   *
   * @param unused Used to mean if open/close icons for tree renderer. No longer in use. The parameter is only there for API compatibility reason.
   */
  @Nullable
  Icon getIcon(boolean unused);
}
