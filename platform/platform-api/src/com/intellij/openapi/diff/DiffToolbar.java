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
package com.intellij.openapi.diff;

import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

public interface DiffToolbar {
  /**
   * An action can access diff view via {@link com.intellij.openapi.actionSystem.DataContext}.
   * @see com.intellij.openapi.actionSystem.PlatformDataKeys#DIFF_VIEWER
   * @see AnAction#update(com.intellij.openapi.actionSystem.AnActionEvent)
   * @see AnAction#actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent)
   * @see com.intellij.openapi.actionSystem.DataContext
   */ 
  void addAction(@NotNull AnAction action);
  void addSeparator();

  /**
   * Removes action with specified id.
   * @param actionId id of action to remove
   * @return iff action with specified id was found in toolbar.
   */
  boolean removeActionById(String actionId);
}
