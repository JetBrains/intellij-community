// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for actions activated by typing in the editor.
 *
 * @see TypedActionHandlerEx
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
