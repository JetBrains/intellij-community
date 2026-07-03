// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInspection.LocalQuickFix;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Same as {@link com.intellij.codeInsight.intention.IntentionActionWithModCommandFallback} but for {@link LocalQuickFix}.
 */
@ApiStatus.Experimental
public interface LocalQuickFixWithModCommandFallback extends LocalQuickFix {
  /**
   * @return a {@link ModCommandAction} that can be used instead of this quick-fix.
   * The command should provide a similar behavior, but probably miss some advanced features like complex UI or
   * cross-file refactoring, which is not possible to implement using the ModCommand API.
   */
  @NotNull ModCommandAction getFallbackModCommandAction();

  /**
   * @param fix quick-fix to find the fallback ModCommandAction for
   * @return the fallback {@link ModCommandAction} if available, otherwise null
   */
  static @Nullable ModCommandAction getFallbackModCommandActionFor(@NotNull LocalQuickFix fix) {
    return fix instanceof LocalQuickFixWithModCommandFallback fallback ? fallback.getFallbackModCommandAction() : null;
  }
}
