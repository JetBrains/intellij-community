// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for macros requiring input from a user (e.g., via input dialog, file chooser, etc.).
 * @see PromptMacro
 */
public abstract class PromptingMacro extends Macro {

  @Override
  protected @Nullable TextRange getRangeForSuffix(@NotNull CharSequence s, int start, int next) {
    return switch (s.charAt(next)) {
      case '$' -> TextRange.create(start, next + 1);
      case ':' -> {
        int end = Strings.indexOf(s, '$', next);
        yield end < 0 ? null : TextRange.create(start, end + 1);
      }
      default -> null;
    };
  }

  @Override
  public String expandOccurence(@NotNull DataContext context, @NotNull String occurence) throws ExecutionCancelledException {
    String[] strings = StringUtil.trimEnd(occurence, "$").split(":");
    String label = strings.length > 1 ? strings[1] : null;
    String defaultValue = strings.length > 2 ? strings[2] : null;
    return expand(context, label, defaultValue);
  }

  private String expand(@NotNull DataContext dataContext, @Nullable String label, @Nullable String defaultValue) throws ExecutionCancelledException {
    Ref<String> userInput = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> userInput.set(promptUser(dataContext, label, defaultValue)));
    if (userInput.isNull()) {
      throw new ExecutionCancelledException();
    }
    return userInput.get();
  }

  @Override
  public final String expand(@NotNull DataContext dataContext) throws ExecutionCancelledException {
    return expand(dataContext, null, null);
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
  protected abstract @Nullable String promptUser(@NotNull DataContext dataContext, @Nullable String label, @Nullable String defaultValue);
}
