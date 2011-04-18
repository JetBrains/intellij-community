/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Ref;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.and;
import static com.intellij.patterns.StandardPatterns.not;
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
    extend(CompletionType.SMART, and(JavaSmartCompletionContributor.INSIDE_EXPRESSION,
                                                      not(psiElement().afterLeaf("."))), new ExpectedTypeBasedCompletionProvider() {
      protected void addCompletions(final CompletionParameters parameters,
                                    final CompletionResultSet result, final Collection<ExpectedTypeInfo> infos) {
        final Ref<Boolean> empty = Ref.create(true);
        result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
          public void consume(final CompletionResult lookupElement) {
            empty.set(false);
            result.passResult(lookupElement);
          }
        });

        @NonNls final String prefix = result.getPrefixMatcher().getPrefix();
        if (empty.get().booleanValue() && prefix.startsWith("n")) {
          for (final ExpectedTypeInfo info : infos) {
            if (!(info.getType() instanceof PsiPrimitiveType)) {
              final LookupItem item = (LookupItem)BasicExpressionCompletionContributor.createKeywordLookupItem(parameters.getPosition(), PsiKeyword.NULL);
              item.setAttribute(LookupItem.TYPE, PsiType.NULL);
              result.addElement(JavaSmartCompletionContributor.decorate(item, infos));
              return;
            }
          }
        }
      }
    });
  }

}
