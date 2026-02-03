// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.annotations.NotNull;

public interface IntentionFilterOwner {
  /**
   * Sets the intention actions filter which is used to determine which intention actions should be available in an editor.
   *
   * @param filter the intention actions filter instance.
   */
  void setIntentionActionsFilter(@NotNull  IntentionActionsFilter filter);

  /**
   * Sets the intention actions filter which is used to determine which intention actions should be available in an editor.
   *
   * @return the intention actions filter instance.
   */
  IntentionActionsFilter getIntentionActionsFilter();

  /**
   * Interface to control the available intention actions.
   */
  interface IntentionActionsFilter {

    /**
     * Checks if the intention action should be available in an editor.
     * @param intentionAction the intention action to analyze
     * @return Returns true if the intention action should be available, false otherwise
     */
    boolean isAvailable(@NotNull IntentionAction intentionAction);

    /**
     * This filter reports all intentions are available.
     */
    IntentionActionsFilter EVERYTHING_AVAILABLE = new IntentionActionsFilter() {
      @Override
      public boolean isAvailable(final @NotNull IntentionAction intentionAction) {
        return true;
      }
    };
  }
}
