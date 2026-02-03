// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.PrefixMatcher;
import org.jetbrains.annotations.NotNull;

public interface WeighingContext {
  /**
   * @return prefix used to match this element. Can be bigger than the {@code itemMatcher(element).getPrefix()}.
   */
  @NotNull
  String itemPattern(@NotNull LookupElement element);

  /**
   * @param item the item to match.
   * @return the prefix matcher used to match this item.
   */
  @NotNull
  PrefixMatcher itemMatcher(@NotNull LookupElement item);
}
