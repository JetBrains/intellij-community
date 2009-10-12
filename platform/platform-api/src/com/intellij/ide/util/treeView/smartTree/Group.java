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

import com.intellij.navigation.ItemPresentation;

import java.util.Collection;

/**
 * A group of items in a generic tree.
 *
 * @see Grouper#group(com.intellij.ide.util.treeView.AbstractTreeNode, java.util.Collection)  
 */
public interface Group {
  /**
   * Returns the presentation information for the group.
   *
   * @return the group presentation.
   */
  ItemPresentation getPresentation();

  /**
   * Returns the list of nodes in the group.
   *
   * @return the list of nodes.
   */
  Collection<TreeElement> getChildren();
}
