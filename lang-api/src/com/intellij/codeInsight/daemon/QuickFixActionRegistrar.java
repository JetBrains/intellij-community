package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.TextRange;

public interface QuickFixActionRegistrar {
  void register(IntentionAction action);
  void register(TextRange fixRange, IntentionAction action, HighlightDisplayKey key);
}
