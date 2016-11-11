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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for actions activated by typing in the editor.
 *
 * @see TypedAction#setupHandler(TypedActionHandler)
 */
public interface TypedActionHandler {
  /**
   * Processes a key typed in the editor. The handler is responsible for delegating to
   * the previously registered handler if it did not handle the typed key.
   *
   * @param editor      the editor in which the key was typed.
   * @param charTyped   the typed character.
   * @param dataContext the current data context.
   */
  void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext);
}
