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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A grouping, sorting or filtering action which can be applied to a generic tree.
 *
 * @see TreeModel#getFilters()
 * @see TreeModel#getGroupers()
 * @see TreeModel#getSorters()
 */

public interface TreeAction {
  /**
   * Returns the presentation for the action.
   *
   * @return the action presentation.
   * @see ActionPresentationData#ActionPresentationData(String, String, javax.swing.Icon)
   */
  @NotNull ActionPresentation getPresentation();

  /**
   * Returns a unique identifier for the action.
   *
   * @return the action identifier.
   */
  @NonNls @NotNull String getName();
}
