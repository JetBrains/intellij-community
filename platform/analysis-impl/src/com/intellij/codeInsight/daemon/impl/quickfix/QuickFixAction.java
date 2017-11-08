// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public final class QuickFixAction {
  private QuickFixAction() { }

  public static void registerQuickFixAction(@Nullable HighlightInfo info, @Nullable IntentionAction action, @Nullable HighlightDisplayKey key) {
    registerQuickFixAction(info, null, action, key);
  }

  public static void registerQuickFixAction(@Nullable HighlightInfo info, @Nullable IntentionAction action) {
    registerQuickFixAction(info, null, action);
  }

  /** This is used by TeamCity plugin */
  @Deprecated
  public static void registerQuickFixAction(@Nullable HighlightInfo info,
                                            @Nullable IntentionAction action,
                                            @Nullable List<IntentionAction> options,
                                            @Nullable String displayName) {
    if (info == null) return;
    info.registerFix(action, options, displayName, null, null);
  }


  public static void registerQuickFixAction(@Nullable HighlightInfo info,
                                            @Nullable TextRange fixRange,
                                            @Nullable IntentionAction action,
                                            @Nullable final HighlightDisplayKey key) {
    if (info == null) return;
    info.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(key), fixRange, key);
  }

  public static void registerQuickFixAction(@Nullable HighlightInfo info, @Nullable TextRange fixRange, @Nullable IntentionAction action) {
    if (info == null) return;
    info.registerFix(action, null, null, fixRange, null);
  }

  public static void registerQuickFixActions(@Nullable HighlightInfo info,
                                             @Nullable TextRange fixRange,
                                             @NotNull Iterable<IntentionAction> actions) {
    for (IntentionAction action : actions) {
      registerQuickFixAction(info, fixRange, action);
    }
  }
}
