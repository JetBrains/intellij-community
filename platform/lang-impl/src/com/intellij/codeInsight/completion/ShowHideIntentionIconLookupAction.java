// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ShowHideIntentionIconLookupAction extends LookupElementAction {
  static final String KEY = "completion.show.intention.icon";

  public ShowHideIntentionIconLookupAction() {
    super(AllIcons.Actions.IntentionBulb, (shouldShowLookupHint() ? CodeInsightBundle.message("lookup.action.never.show.intention.icon")
                                                                  : CodeInsightBundle.message("lookup.action.show.intention.icon")));
  }

  public static boolean shouldShowLookupHint() {
    return Registry.is(KEY);
  }

  @Override
  public Result performLookupAction() {
    Registry.get(KEY).setValue(!shouldShowLookupHint());
    return Result.REFRESH_ITEM;
  }
}
