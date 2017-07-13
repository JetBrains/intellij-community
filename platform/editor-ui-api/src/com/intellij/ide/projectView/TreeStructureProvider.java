/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Allows a plugin to modify the structure of a project as displayed in the project view.
 */
public interface TreeStructureProvider {
  ExtensionPointName<TreeStructureProvider> EP_NAME = ExtensionPointName.create("com.intellij.treeStructureProvider");

  /**
   * Allows a plugin to modify the list of children displayed for the specified node in the
   * project view.
   *
   * @param parent   the parent node.
   * @param children the list of child nodes according to the default project structure.
   *                 Elements of the collection are of type {@link ProjectViewNode}.
   * @param settings the current project view settings.
   * @return the modified collection of child nodes, or {@code children} if no modifications
   *         are required.
   */
  @NotNull
  Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode> children, ViewSettings settings);

  /**
   * Returns a user data object of the specified type for the specified selection in the
   * project view.
   *
   * @param selected the list of nodes currently selected in the project view.
   * @param dataName the identifier of the requested data object (for example, as defined in
   *                 {@link com.intellij.openapi.actionSystem.PlatformDataKeys})
   * @return the data object, or null if no data object can be returned by this provider.
   * @see com.intellij.openapi.actionSystem.DataProvider
   */
  @Nullable
  default Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}
