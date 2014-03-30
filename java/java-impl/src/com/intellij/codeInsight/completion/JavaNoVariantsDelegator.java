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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaNoVariantsDelegator extends CompletionContributor {

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, CompletionResultSet result) {
    LinkedHashSet<CompletionResult> plainResults = result.runRemainingContributors(parameters, true);
    final boolean empty = containsOnlyPackages(plainResults) || suggestMetaAnnotations(parameters);

    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty) {
      delegate(parameters, JavaCompletionSorting.addJavaSorting(parameters, result));
    } else if (Registry.is("ide.completion.show.better.matching.classes")) {
      if (parameters.getCompletionType() == CompletionType.BASIC &&
          parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result) &&
          JavaCompletionContributor.isClassNamePossible(parameters) &&
          !JavaSmartCompletionContributor.AFTER_NEW.accepts(parameters.getPosition())) {
        result = result.withPrefixMatcher(new BetterPrefixMatcher(result.getPrefixMatcher(), BetterPrefixMatcher.getBestMatchingDegree(plainResults)));
        InheritorsHolder holder = new InheritorsHolder(parameters.getPosition(), result);
        for (CompletionResult plainResult : plainResults) {
          LookupElement element = plainResult.getLookupElement();
          if (element instanceof TypeArgumentCompletionProvider.TypeArgsLookupElement) {
            ((TypeArgumentCompletionProvider.TypeArgsLookupElement)element).registerSingleClass(holder);
          }
        }
        suggestNonImportedClasses(parameters, JavaCompletionSorting.addJavaSorting(parameters, result), holder);
      }
    }
  }

  private static boolean suggestMetaAnnotations(CompletionParameters parameters) {
    PsiElement position = parameters.getPosition();
    return psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class, PsiModifierList.class, PsiClass.class).accepts( position) &&
           psiElement().withSuperParent(4, psiClass().isAnnotationType()).accepts(position);
  }

  public static boolean containsOnlyPackages(LinkedHashSet<CompletionResult> results) {
    for (CompletionResult result : results) {
      if (!(CompletionUtil.getTargetElement(result.getLookupElement()) instanceof PsiPackage)) {
        return false;
      }
    }
    return true;
  }

  private static void delegate(CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      PsiElement position = parameters.getPosition();
      suggestCollectionUtilities(parameters, result, position);

      if (parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result) &&
          JavaCompletionContributor.isClassNamePossible(parameters)) {
        suggestNonImportedClasses(parameters, result, null);
        return;
      }

      suggestChainedCalls(parameters, result, position);
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 2) {
      result.runRemainingContributors(parameters.withInvocationCount(3), true);
    }
  }

  private static void suggestCollectionUtilities(CompletionParameters parameters, final CompletionResultSet result, PsiElement position) {
    if (StringUtil.isNotEmpty(result.getPrefixMatcher().getPrefix())) {
      for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new CollectionsUtilityMethodsProvider(position, info.getType(), info.getDefaultType(), result).addCompletions(true);
      }
    }
  }

  private static void suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result, PsiElement position) {
    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement) || parent.getParent() instanceof PsiImportStatementBase) {
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

    PsiFile file = position.getContainingFile();
    if (file instanceof PsiJavaCodeReferenceCodeFragment) {
      return;
    }

    String fullPrefix = parent.getText().substring(0, parameters.getOffset() - parent.getTextRange().getStartOffset());
    CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    for (LookupElement base : suggestQualifierItems(parameters, (PsiJavaCodeReferenceElement)qualifier, filter)) {
      PsiType type = JavaCompletionUtil.getLookupElementType(base);
      if (type != null && !PsiType.VOID.equals(type)) {
        PsiReferenceExpression ref = ReferenceExpressionCompletionContributor.createMockReference(position, type, base);
        if (ref != null) {
          for (final LookupElement item : JavaSmartCompletionContributor.completeReference(position, ref, filter, true, true, parameters,
                                                                                           result.getPrefixMatcher())) {
            qualifiedCollector.addElement(new JavaChainLookupElement(base, item));
          }
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

  private static void suggestNonImportedClasses(CompletionParameters parameters, final CompletionResultSet result, @Nullable final InheritorsHolder inheritorsHolder) {
    JavaClassNameCompletionContributor.addAllClasses(parameters,
                                                     true, result.getPrefixMatcher(), new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        if (inheritorsHolder != null && inheritorsHolder.alreadyProcessed(element)) {
          return;
        }
        JavaPsiClassReferenceElement classElement = element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
        if (classElement != null) {
          classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }

        result.addElement(element);
      }
    });
  }
}
