/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class JavaNoVariantsDelegator extends CompletionContributor {

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    final boolean empty = containsOnlyPackages(result.runRemainingContributors(parameters, true));

    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty) {
      delegate(parameters, result);
    }
  }

  public static boolean containsOnlyPackages(LinkedHashSet<CompletionResult> results) {
    for (CompletionResult result : results) {
      if (!(CompletionUtil.getTargetElement(result.getLookupElement()) instanceof PsiPackage)) {
        return false;
      }
    }
    return true;
  }

  private static void delegate(CompletionParameters parameters, CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      PsiElement position = parameters.getPosition();
      if (parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result, false) &&
          JavaCompletionContributor.isClassNamePossible(position)) {
        suggestNonImportedClasses(parameters, result);
        return;
      }

      suggestChainedCalls(parameters, result, position);
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 2) {
      result.runRemainingContributors(parameters.withInvocationCount(3), true);
    }
  }

  private static void suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result, PsiElement position) {
    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) {
      return;
    }
    PsiElement qualifier = ((PsiJavaCodeReferenceElement)parent).getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement) ||
        ((PsiJavaCodeReferenceElement)qualifier).isQualified()) {
      return;
    }
    PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
    if (target != null && !(target instanceof PsiPackage)) {
      return;
    }

    String fullPrefix = position.getContainingFile().getText().substring(parent.getTextRange().getStartOffset(), parameters.getOffset());
    CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    for (LookupElement base : suggestQualifierItems(parameters, (PsiJavaCodeReferenceElement)qualifier, filter)) {
      PsiType type = JavaCompletionUtil.getLookupElementType(base);
      if (type != null && !PsiType.VOID.equals(type)) {
        PsiReferenceExpression ref = ReferenceExpressionCompletionContributor.createMockReference(position, type, base);
        for (final LookupElement item : JavaSmartCompletionContributor.completeReference(position, ref, filter, true, true, parameters,
                                                                                         result.getPrefixMatcher())) {
          qualifiedCollector.addElement(new JavaChainLookupElement(base, item));
        }
      }
    }
  }

  private static Set<LookupElement> suggestQualifierItems(CompletionParameters parameters,
                                                          PsiJavaCodeReferenceElement qualifier,
                                                          ElementFilter filter) {
    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) {
      return Collections.emptySet();
    }

    PrefixMatcher qMatcher = new CamelHumpMatcher(referenceName);
    Set<LookupElement> plainVariants =
      JavaSmartCompletionContributor.completeReference(qualifier, qualifier, filter, true, true, parameters, qMatcher);

    for (PsiClass aClass : PsiShortNamesCache.getInstance(qualifier.getProject()).getClassesByName(referenceName, qualifier.getResolveScope())) {
      plainVariants.add(JavaClassNameCompletionContributor.createClassLookupItem(aClass, true));
    }

    if (!plainVariants.isEmpty()) {
      return plainVariants;
    }

    final Set<LookupElement> allClasses = new LinkedHashSet<LookupElement>();
    JavaClassNameCompletionContributor.addAllClasses(parameters.withPosition(qualifier.getReferenceNameElement(), qualifier.getTextRange().getEndOffset()),
                                                     true, qMatcher, new CollectConsumer<LookupElement>(allClasses));
    return allClasses;
  }

  private static void suggestNonImportedClasses(CompletionParameters parameters, CompletionResultSet result) {
    final ClassByNameMerger merger = new ClassByNameMerger(parameters.getInvocationCount() == 0, result);

    JavaClassNameCompletionContributor.addAllClasses(parameters,
                                                     true, JavaCompletionSorting.addJavaSorting(parameters, result).getPrefixMatcher(), new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        JavaPsiClassReferenceElement classElement = element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
        if (classElement != null) {
          classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }

        merger.consume(classElement);
      }
    });

    merger.finishedClassProcessing();
  }
}
