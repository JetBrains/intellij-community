/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

/**
 * @author peter
 */
public class NullSmartCompletionContributor extends CompletionContributor{
  public NullSmartCompletionContributor() {
    extend(CompletionType.SMART, JavaSmartCompletionContributor.INSIDE_EXPRESSION, new ExpectedTypeBasedCompletionProvider(false) {
      protected void addCompletions(final CompletionParameters parameters,
                                    final CompletionResultSet result, final Collection<ExpectedTypeInfo> infos) {
        final Ref<Boolean> empty = Ref.create(true);
        CompletionService.getCompletionService().getVariantsFromContributors(EP_NAME, parameters, NullSmartCompletionContributor.this, new Consumer<LookupElement>() {
          public void consume(final LookupElement lookupElement) {
            empty.set(false);
            result.addElement(lookupElement);
          }
        });

        @NonNls final String prefix = result.getPrefixMatcher().getPrefix();
        if (empty.get().booleanValue() && prefix.startsWith("n")) {
          for (final ExpectedTypeInfo info : infos) {
            if (!(info.getType() instanceof PsiPrimitiveType)) {
              final LookupItem item = BasicExpressionCompletionContributor.createKeywordLookupItem(parameters.getPosition(), PsiKeyword.NULL);
              item.setAttribute(LookupItem.TYPE, PsiType.NULL);
              result.addElement(item);
              return;
            }
          }
        }
      }
    });
  }

}
