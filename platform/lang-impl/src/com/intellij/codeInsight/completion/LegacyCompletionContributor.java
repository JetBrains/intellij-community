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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.paths.PsiDynaReference;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class LegacyCompletionContributor extends CompletionContributor {
  public static boolean DEBUG = false;

  public LegacyCompletionContributor() {
    final PsiElementPattern.Capture<PsiElement> everywhere = PlatformPatterns.psiElement();
    extend(CompletionType.BASIC, everywhere, new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters,
                                 final ProcessingContext matchingContext,
                                 @NotNull final CompletionResultSet _result) {
        final PsiFile file = parameters.getOriginalFile();
        final int startOffset = parameters.getOffset();
        final PsiElement insertedElement = parameters.getPosition();
        CompletionData completionData = ApplicationManager.getApplication().runReadAction(new Computable<CompletionData>() {
          public CompletionData compute() {
            return CompletionUtil.getCompletionDataByElement(insertedElement, file);
          }
        });
        if (completionData == null) return;

        final CompletionResultSet result = _result.withPrefixMatcher(completionData.findPrefix(insertedElement, startOffset));


        completeReference(parameters, result, completionData);

        final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        completionData.addKeywordVariants(keywordVariants, insertedElement, parameters.getOriginalFile());
        completionData
          .completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, result.getPrefixMatcher(), file);
        for (final LookupElement item : lookupSet) {
          result.addElement(item);
        }
      }
    });


  }

  public static boolean completeReference(final CompletionParameters parameters,
                                          final CompletionResultSet result,
                                          final CompletionData completionData) {
    final Ref<Boolean> hasVariants = Ref.create(false);
    processReferences(parameters, result, completionData, new PairConsumer<PsiReference, CompletionResultSet>() {
      public void consume(final PsiReference reference, final CompletionResultSet resultSet) {
        final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
        completionData
          .completeReference(reference, lookupSet, parameters.getPosition(), parameters.getOriginalFile(), parameters.getOffset());
        for (final LookupElement item : lookupSet) {
          if (resultSet.getPrefixMatcher().prefixMatches(item)) {
            hasVariants.set(true);
            resultSet.addElement(item);
          }
        }
      }
    });
    return hasVariants.get().booleanValue();
  }

  public static void processReferences(final CompletionParameters parameters,
                                       final CompletionResultSet result,
                                       final CompletionData completionData,
                                       final PairConsumer<PsiReference, CompletionResultSet> consumer) {
    final int startOffset = parameters.getOffset();
    final PsiReference ref = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
      public PsiReference compute() {
        return parameters.getPosition().getContainingFile().findReferenceAt(startOffset);
      }
    });
    if (ref instanceof PsiMultiReference) {
      for (final PsiReference reference : completionData.getReferences((PsiMultiReference)ref)) {
        processReference(result, startOffset, consumer, reference);
      }
    }
    else if (ref instanceof PsiDynaReference) {
      int offset = startOffset - ref.getElement().getTextRange().getStartOffset();
      for (final PsiReference reference : ((PsiDynaReference<?>)ref).getReferences()) {
        if (ReferenceRange.containsOffsetInElement(reference, offset)) {
          processReference(result, startOffset, consumer, reference);
        }
      }
    }
    else if (ref != null) {
      processReference(result, startOffset, consumer, ref);
    }
  }

  private static void processReference(final CompletionResultSet result,
                                       final int startOffset,
                                       final PairConsumer<PsiReference, CompletionResultSet> consumer,
                                       final PsiReference reference) {
    final int offsetInElement = startOffset - reference.getElement().getTextRange().getStartOffset();
    final String prefix = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return reference.getElement().getText().substring(reference.getRangeInElement().getStartOffset(), offsetInElement);
      }
    });
    consumer.consume(reference, result.withPrefixMatcher(prefix));
  }


}
