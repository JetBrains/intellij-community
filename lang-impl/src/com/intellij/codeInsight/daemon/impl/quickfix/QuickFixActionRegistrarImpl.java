package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.TextRange;

public class QuickFixActionRegistrarImpl implements QuickFixActionRegistrar {
  private HighlightInfo myInfo;

  public QuickFixActionRegistrarImpl(HighlightInfo info) {
    myInfo = info;
  }

  public void register(IntentionAction action) {
    QuickFixAction.registerQuickFixAction(myInfo, action);
  }

  public void register(TextRange fixRange, IntentionAction action, HighlightDisplayKey key) {
    QuickFixAction.registerQuickFixAction(myInfo, fixRange, action, key);
  }
}
