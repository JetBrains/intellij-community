package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;

public interface QuickFixActionRegistrar {
  void register(IntentionAction action);
  void register(TextRange fixRange, IntentionAction action, HighlightDisplayKey key);

  /**
   * Allows to replace some of the built-in quickfixes.
   * @param condition condition for quickfixes to remove
   * @since 9.0
   */
  void unregister(Condition<IntentionAction> condition);
}
