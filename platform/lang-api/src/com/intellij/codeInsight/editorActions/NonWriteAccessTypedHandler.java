// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An interface for handling typed characters without write access to the document.
 * Please note that this is an experimental class and can be deleted in the future
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface NonWriteAccessTypedHandler {
  ExtensionPointName<NonWriteAccessTypedHandler> EP_NAME = new ExtensionPointName<>("com.intellij.nonWriteAccessTypedHandler");

  /**
   * Determines whether a specific operation or action is applicable based on the provided editor context, the character typed,
   * and additional contextual data.
   *
   * @param editor      the editor in which the character is typed; must not be null
   * @param charTyped   the character that was typed
   * @param dataContext the data context containing additional information; must not be null
   * @return true if the operation or action is applicable, false otherwise
   */
  boolean isApplicable(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext);

  /**
   * Handles the action performed on the given editor when a character is typed.
   *
   * @param editor the editor instance where the character is typed; must not be null
   * @param charTyped the character that has been typed
   * @param dataContext the data context associated with the action; must not be null
   */
  void handle(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext);
}
