// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * utility class helping to register quick fixes with {@link HighlightInfo}
 */
public final class QuickFixAction {
  private QuickFixAction() { }

  /**
   * @deprecated use {@link HighlightInfo.Builder#registerFix(IntentionAction, List, String, TextRange, HighlightDisplayKey)} instead
   */
  @Deprecated
  public static void registerQuickFixAction(@Nullable HighlightInfo info, @Nullable IntentionAction action) {
    if (info != null && action != null) {
      info.registerFix(action, null, null, null, null);
    }
  }

  public static void registerQuickFixActions(@Nullable HighlightInfo.Builder builder,
                                             @Nullable TextRange fixRange,
                                             @NotNull Iterable<? extends IntentionAction> actions) {
    if (builder != null) {
      for (IntentionAction action : actions) {
        builder.registerFix(action, null, null, fixRange, null);
      }
    }
  }

  /**
   * @deprecated use {@link HighlightInfo.Builder#registerFix(IntentionAction, List, String, TextRange, HighlightDisplayKey)} instead
   */
  @Deprecated
  public static void registerQuickFixActions(@Nullable HighlightInfo info,
                                             @Nullable TextRange fixRange,
                                             @NotNull Iterable<? extends IntentionAction> actions) {
    if (info != null) {
      for (IntentionAction action : actions) {
        info.registerFix(action, null, null, fixRange, null);
      }
    }
  }
}
