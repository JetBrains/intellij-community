// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides a way to reorder context actions (quick-fixes, intentions, etc.) in a specific language context.
 *
 * @see CachedIntentions#getAllActions()
 */
public interface IntentionsOrderProvider {
  LanguageExtension<IntentionsOrderProvider> EXTENSION =
    new LanguageExtension<>("com.intellij.intentionsOrderProvider", new DefaultIntentionsOrderProvider());

  /**
   * Returns context actions in order they are going to be displayed in popup.
   */
  @NotNull List<IntentionActionWithTextCaching> getSortedIntentions(@NotNull CachedIntentions context,
                                                                    @NotNull List<IntentionActionWithTextCaching> intentions);
}