// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IntentionsOrderProvider {
  LanguageExtension<IntentionsOrderProvider> EXTENSION =
    new LanguageExtension<>("com.intellij.intentionsOrderProvider", new DefaultIntentionsOrderProvider());

  @NotNull List<IntentionActionWithTextCaching> getSortedIntentions(@NotNull CachedIntentions context,
                                                                    @NotNull List<IntentionActionWithTextCaching> intentions);
}