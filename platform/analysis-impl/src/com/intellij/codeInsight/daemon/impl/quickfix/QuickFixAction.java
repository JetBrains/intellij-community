/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.TextRange;
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
}
