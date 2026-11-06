// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionWithAny;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A filter that allows to remove specific completion items.
 */
@NotNullByDefault
public interface ModCompletionItemFilter {
  LanguageExtension<ModCompletionItemFilter> EP_NAME = new LanguageExtensionWithAny<>("com.intellij.modcompletion.completionItemFilter");

  /**
   * Called once per completion request for each item provider.
   *
   * @param provider provider whose items are to be filtered
   * @param context completion context
   * @return the test for the items of {@code provider}: true if the item should be kept, false if it should be filtered out;
   * null if this filter does not apply to {@code provider} in {@code context}.
   * The item is filtered out if at least one returned test returns false.
   */
  @Nullable Predicate<ModCompletionItem> createFilter(ModCompletionItemProvider provider,
                                                      ModCompletionItemProvider.CompletionContext context);
}
