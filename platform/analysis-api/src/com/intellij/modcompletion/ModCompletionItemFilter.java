// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionWithAny;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * A filter that allows to remove specific completion items.
 */
@NotNullByDefault
public interface ModCompletionItemFilter {
  LanguageExtension<ModCompletionItemFilter> EP_NAME = new LanguageExtensionWithAny<>("com.intellij.modcompletion.completionItemFilter");

  /**
   * @param provider provider
   * @return true if this filter is applicable for a specific item provider. 
   * It will not filter items from other providers.
   */
  boolean isApplicableFor(ModCompletionItemProvider provider);

  /**
   * @param context completion context
   * @param item item to check
   * @return true if the item should be kept; false if it should be filtered out.
   * The item is filtered out if at least one registered filter returns false, 
   * so normally this method should return true for any unrecognized item.
   */
  boolean test(ModCompletionItemProvider.CompletionContext context, ModCompletionItem item);
}
