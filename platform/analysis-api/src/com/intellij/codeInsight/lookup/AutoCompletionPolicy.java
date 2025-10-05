// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NotNull;

/**
 * What to do if there's only one element in completion lookup? Should the IDE show lookup or just insert this element? Call
 * {@link #applyPolicy(LookupElement)} to decorate {@link LookupElement} with correct policy.
 *
 * Use this only in simple cases, use {@link com.intellij.codeInsight.completion.CompletionContributor#handleAutoCompletionPossibility(com.intellij.codeInsight.completion.AutoCompletionContext)}
 * for finer tuning.
 */
public enum AutoCompletionPolicy {
  /**
   * Self-explaining.
   */
  NEVER_AUTOCOMPLETE,

  /**
   * If 'auto-complete if only one choice' is configured in settings, the item will be inserted, otherwise - no.
   *
   * @see com.intellij.codeInsight.CodeInsightSettings#AUTOCOMPLETE_ON_CODE_COMPLETION
   * @see com.intellij.codeInsight.CodeInsightSettings#AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION
   */
  SETTINGS_DEPENDENT,

  /**
   * If caret is positioned inside an identifier, and 'auto-complete if only one choice' is configured in settings,
   * a lookup with one item will still open, giving user a chance to overwrite the identifier using Tab key
   */
  GIVE_CHANCE_TO_OVERWRITE,

  /**
   * Self-explaining.
   */
  ALWAYS_AUTOCOMPLETE;

  public @NotNull LookupElement applyPolicy(@NotNull LookupElement element) {
    return new PolicyDecorator(element, this);
  }

  private static final class PolicyDecorator extends LookupElementDecorator<LookupElement> {
    private final AutoCompletionPolicy myPolicy;

    PolicyDecorator(LookupElement element, AutoCompletionPolicy policy) {
      super(element);
      myPolicy = policy;
    }

    @Override
    public AutoCompletionPolicy getAutoCompletionPolicy() {
      return myPolicy;
    }
  }
}
