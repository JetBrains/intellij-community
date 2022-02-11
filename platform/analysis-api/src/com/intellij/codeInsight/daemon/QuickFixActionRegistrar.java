// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface QuickFixActionRegistrar {
  QuickFixActionRegistrar IGNORE_ALL = new QuickFixActionRegistrar() {
    @Override
    public void register(@NotNull IntentionAction action) {
    }
    @Override
    public void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, @Nullable HighlightDisplayKey key) {
    }
  };

  void register(@NotNull IntentionAction action);

  void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, @Nullable HighlightDisplayKey key);

  /**
   * Allows to replace some of the built-in quick fixes.
   *
   * @param condition condition for quick fixes to remove
   * @deprecated if some fix may be inapplicable under certain circumstances
   * it should be fixed to provide its own EP, so it's possible to plug into the fix directly
   * instead of filtering it with this method
   */
  @Deprecated(forRemoval = true)
  default void unregister(@NotNull Condition<? super IntentionAction> condition) {}
}
