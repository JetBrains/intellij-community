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

package com.intellij.lang;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface ContextAwareActionHandler {
  /**
   * Handlers could provide useful hints when they are actually not available, e.g.
   * 'No methods to implement', 'Selected block should represent ...', 'Caret should be positioned at the name of ...', etc.
   * At the same time, when the action is invoked through refactorings quick list popup,
   * generate popup or in another manner but not through main menu (shortcut or find action are treated the same),
   * it's better to hide the action: it would pollute menu with one more choice but can't do anything.
   *
   * @return It's assumed that handler is valid for file. Still should be lightweight, because is invoked from action update.
   *         false - if action won't proceed
   */
  boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext);
}
