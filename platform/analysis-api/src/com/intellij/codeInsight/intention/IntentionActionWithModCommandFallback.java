// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A classic intention action that has an alternative {@link ModCommand}-based implementation.
 * It's still preferred to use this action in IntelliJ platform IDEs, but it could be useful to
 * use ModCommand in other contexts, like headless applications. 
 */
@ApiStatus.Experimental
public interface IntentionActionWithModCommandFallback extends IntentionAction {
  /**
   * @return a ModCommandAction that can be used instead of this intention action.
   * The command should provide a similar behavior, but probably miss some advanced features like complex UI,
   * which is not possible to implement using the ModCommand API.
   */
  @Nullable ModCommandAction getFallbackModCommandAction();

  /**
   * @param action action to find the fallback ModCommandAction for
   * @return the fallback {@link ModCommandAction} if available, otherwise null
   */
  static @Nullable ModCommandAction getFallbackModCommandActionFor(@NotNull IntentionAction action) {
    while (true) {
      if (action instanceof IntentionActionWithModCommandFallback actionWithModCommandFallback) {
        return actionWithModCommandFallback.getFallbackModCommandAction();
      }
      if (action instanceof IntentionActionDelegate delegate) {
        action = delegate.getDelegate();
      } else {
        return null;
      }
    }
  }
}
