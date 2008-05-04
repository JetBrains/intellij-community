/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.Consumer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CompletionServiceImpl extends CompletionService{
  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<LookupElement> consumer) {
    final PsiElement position = parameters.getPosition();
    final CompletionContext context = position.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
    final String prefix = CompletionData.findPrefixStatic(position, parameters.getOffset());
    context.setPrefix(prefix);

    return new CompletionResultSet(new CamelHumpMatcher(prefix)) {
      public void addElement(@NotNull final LookupElement result) {
        PrefixMatcher matcher = result.getUserData(CompletionUtil.PREFIX_MATCHER);
        if (matcher == null) {
          matcher = getPrefixMatcher();
          result.putUserData(CompletionUtil.PREFIX_MATCHER, matcher);
          if (!matcher.prefixMatches(result)) return;
        }
        consumer.consume(result);
      }

      public void setPrefixMatcher(@NotNull final PrefixMatcher matcher) {
        super.setPrefixMatcher(matcher);
        context.setPrefix(matcher.getPrefix());
      }

      public void setPrefixMatcher(@NotNull final String prefix) {
        setPrefixMatcher(new CamelHumpMatcher(prefix));
      }
    };
  }
}
