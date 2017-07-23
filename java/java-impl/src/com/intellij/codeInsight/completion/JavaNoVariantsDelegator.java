/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaNoVariantsDelegator extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    final JavaCompletionSession session = new JavaCompletionSession(result);
    ResultTracker tracker = new ResultTracker(result) {
      @Override
      public void consume(CompletionResult plainResult) {
        super.consume(plainResult);

        LookupElement element = plainResult.getLookupElement();
        Object o = element.getObject();
        if (o instanceof PsiClass) {
          session.registerClass((PsiClass)o);
        }
        if (element instanceof TypeArgumentCompletionProvider.TypeArgsLookupElement) {
          ((TypeArgumentCompletionProvider.TypeArgsLookupElement)element).registerSingleClass(session);
        }
      }
    };
    result.runRemainingContributors(parameters, tracker);
    final boolean empty = tracker.containsOnlyPackages || suggestAllAnnotations(parameters);

    if (JavaCompletionContributor.isClassNamePossible(parameters) && !JavaCompletionContributor.mayStartClassName(result)) {
      result.restartCompletionOnAnyPrefixChange();
    }

    if (empty) {
      delegate(parameters, JavaCompletionSorting.addJavaSorting(parameters, result), session);
    } else if (Registry.is("ide.completion.show.better.matching.classes")) {
      if (parameters.getCompletionType() == CompletionType.BASIC &&
          parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result) &&
          JavaCompletionContributor.isClassNamePossible(parameters) &&
          !areNonImportedInheritorsAlreadySuggested(parameters)) {
        suggestNonImportedClasses(parameters, JavaCompletionSorting.addJavaSorting(parameters, result.withPrefixMatcher(tracker.betterMatcher)), session);
      }
    }
  }

  private static boolean areNonImportedInheritorsAlreadySuggested(@NotNull CompletionParameters parameters) {
    return JavaSmartCompletionContributor.AFTER_NEW.accepts(parameters.getPosition()) &&
           JavaSmartCompletionContributor.getExpectedTypes(parameters).length > 0;
  }

  private static boolean suggestAllAnnotations(CompletionParameters parameters) {
    return psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class).accepts(parameters.getPosition());
  }

  private static void delegate(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      PsiElement position = parameters.getPosition();
      suggestCollectionUtilities(parameters, result, position);

      if (parameters.getInvocationCount() <= 1 &&
          (JavaCompletionContributor.mayStartClassName(result) || suggestAllAnnotations(parameters)) &&
          JavaCompletionContributor.isClassNamePossible(parameters)) {
        suggestNonImportedClasses(parameters, result, session);
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
        String separator = parent instanceof PsiMethodReferenceExpression ? "::" : ".";
        PsiReferenceExpression ref = ReferenceExpressionCompletionContributor.createMockReference(position, type, base, separator);
        if (ref != null) {
          for (LookupElement item : JavaSmartCompletionContributor.completeReference(position, ref, filter, true, true, parameters,
                                                                                     result.getPrefixMatcher())) {
            qualifiedCollector.addElement(JavaCompletionUtil.highlightIfNeeded(null, new JavaChainLookupElement(base, item, separator), item.getObject(), position));
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

    final Set<LookupElement> allClasses = new LinkedHashSet<>();
    PsiElement qualifierName = qualifier.getReferenceNameElement();
    if (qualifierName != null) {
      JavaClassNameCompletionContributor.addAllClasses(parameters.withPosition(qualifierName, qualifierName.getTextRange().getEndOffset()),
                                                       true, qMatcher, new CollectConsumer<>(allClasses));
    }
    return allClasses;
  }

  private static void suggestNonImportedClasses(CompletionParameters parameters, CompletionResultSet result, @Nullable JavaCompletionSession session) {
    JavaClassNameCompletionContributor.addAllClasses(parameters, true, result.getPrefixMatcher(), element -> {
      if (session != null && session.alreadyProcessed(element)) {
        return;
      }
      JavaPsiClassReferenceElement classElement = element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
      if (classElement != null) {
        classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        element = JavaClassNameCompletionContributor.highlightIfNeeded(classElement, parameters);
      }

      result.addElement(element);
    });
  }

  public static class ResultTracker implements Consumer<CompletionResult> {
    private final CompletionResultSet myResult;
    public boolean containsOnlyPackages = true;
    public BetterPrefixMatcher betterMatcher;

    public ResultTracker(CompletionResultSet result) {
      myResult = result;
      betterMatcher = new BetterPrefixMatcher.AutoRestarting(result);
    }

    @Override
    public void consume(CompletionResult plainResult) {
      myResult.passResult(plainResult);

      LookupElement element = plainResult.getLookupElement();
      if (containsOnlyPackages && !(CompletionUtil.getTargetElement(element) instanceof PsiPackage)) {
        containsOnlyPackages = false;
      }

      betterMatcher = betterMatcher.improve(plainResult);
    }
  }
}
