// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Provides an {@link UndoManager} for a specific UI/toolkit context (e.g., Compose).
 */
@ApiStatus.Internal
public interface UndoManagerProvider {
  ExtensionPointName<UndoManagerProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.undoManagerProvider");

  @Nullable UndoManager getUndoManager(DataContext dataContext);
}
