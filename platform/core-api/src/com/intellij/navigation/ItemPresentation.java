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
package com.intellij.navigation;

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
  @Nullable
  String getPresentableText();

  /**
   * Returns the location of the object (for example, the package of a class). The location
   * string is used by some renderers and usually displayed as grayed text next to the item name.
   *
   * @return the location description, or null if none is applicable.
   */
  @Nullable
  String getLocationString();

  /**
   * Returns the icon representing the object.
   *
   * @param unused Used to mean if open/close icons for tree renderer. No longer in use. The parameter is only there for API compatibility reason.
   */
  @Nullable
  Icon getIcon(boolean unused);
}
