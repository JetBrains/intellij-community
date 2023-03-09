// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QuickFixActionRegistrarImpl implements QuickFixActionRegistrar {
  @NotNull
  private final HighlightInfo myInfo;

  public QuickFixActionRegistrarImpl(@NotNull HighlightInfo info) {
    myInfo = info;
  }

  @Override
  public void register(@NotNull IntentionAction action) {
    doRegister(action, null, null, null);
  }

  @Override
  public void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, HighlightDisplayKey key) {
    doRegister(action, HighlightDisplayKey.getDisplayNameByKey(key), fixRange, key);
  }

  void doRegister(@NotNull IntentionAction action,
                  @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String displayName,
                  @Nullable TextRange fixRange,
                  @Nullable HighlightDisplayKey key) {
    myInfo.registerFix(action, null, displayName, fixRange, key);
  }

  @Override
  public void unregister(@NotNull Condition<? super IntentionAction> condition) {
    myInfo.unregisterQuickFix(condition);
  }

  @Override
  public String toString() {
    return "QuickFixActionRegistrarImpl{myInfo=" + myInfo + '}';
  }
}
