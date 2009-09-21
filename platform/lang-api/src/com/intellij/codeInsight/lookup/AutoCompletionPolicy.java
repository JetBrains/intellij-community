/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What to do if there's only one element in completion lookup? Should IDEA show lookup or just insert this element? Call
 * {@link #applyPolicy(LookupElement)} to decorate {@link com.intellij.codeInsight.lookup.LookupElement} with correct policy.
 *
 * Use this only in simple cases, use {@link com.intellij.codeInsight.completion.CompletionContributor#handleAutoCompletionPossibility(com.intellij.codeInsight.completion.AutoCompletionContext)}
 * for finer tuning.
 *
 * @author peter
 */
public enum AutoCompletionPolicy {
  /**
   * Self-explaining
   */
  NEVER_AUTOCOMPLETE,

  /**
   * If 'auto-complete if only one choice' is configured in settings, the item will be inserted, otherwise - no.
   */
  SETTINGS_DEPENDENT,

  /**
   * If caret is positioned inside an identifier, and 'auto-complete if only one choice' is configured in settings,
   * a lookup with one item will still open, giving user a chance to overwrite the identifier using Tab key
   */
  GIVE_CHANCE_TO_OVERWRITE,

  /**
   * Self-explaining
   */
  ALWAYS_AUTOCOMPLETE;

  @NotNull
  public LookupElement applyPolicy(@NotNull LookupElement element) {
    return new PolicyDecorator(element, this);
  }

  @Nullable
  public static AutoCompletionPolicy getPolicy(LookupElement element) {
    final PolicyDecorator decorator = element.as(PolicyDecorator.class);
    if (decorator != null) {
      return decorator.myPolicy;
    }
    return null;
  }

  private static class PolicyDecorator extends LookupElementDecorator<LookupElement> {
    private final AutoCompletionPolicy myPolicy;

    public PolicyDecorator(LookupElement element, AutoCompletionPolicy policy) {
      super(element);
      myPolicy = policy;
    }

  }
}
