// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for macros requiring input from a user (e.g., via input dialog, file chooser, etc.).
 * @see PromptMacro
 */
public abstract class PromptingMacro extends Macro {

  @Override
  public final String expand(@NotNull DataContext dataContext) throws ExecutionCancelledException {
    Ref<String> userInput = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> userInput.set(promptUser(dataContext)));
    if (userInput.isNull()) {
      throw new ExecutionCancelledException();
    }
    return userInput.get();
  }

  @Override
  public @Nullable String preview(@NotNull DataContext dataContext) {
    return "<params>";
  }

  /**
   * Called from the {@link #expand} methods.
   *
   * @return user input.
   * If {@code null} is returned, {@code ExecutionCancelledException} is thrown by the {@link #expand} method.
   */
  @Nullable
  protected abstract String promptUser(DataContext dataContext);
}
