/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.CastTypeGetter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.ThrowsListGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.filters.types.AssignableToFilter;
import com.intellij.psi.filters.types.ReturnTypeFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ReferenceExpressionCompletionContributor extends ExpressionSmartCompletionContributor{
  public static final OrFilter THROWABLE_TYPE_FILTER = new OrFilter(
      new GeneratorFilter(AssignableGroupFilter.class, new ThrowsListGetter()),
      new AssignableFromFilter("java.lang.RuntimeException"));

  @Nullable
  private static Pair<ElementFilter, TailType> getReferenceFilter(PsiElement element) {
    final ReturnTypeFilter valueTypeFilter =
        new ReturnTypeFilter(new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter()));

    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ReturnTypeFilter(THROWABLE_TYPE_FILTER), TailType.SEMICOLON);
    }

    if (psiElement().afterLeaf(psiElement().withText(")").withParent(PsiTypeCastExpression.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ReturnTypeFilter(new GeneratorFilter(AssignableToFilter.class, new CastTypeGetter())), TailType.NONE);
    }

    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new AndFilter(valueTypeFilter,
                                                             new ElementExtractorFilter(new ExcludeDeclaredFilter(new ClassFilter(PsiMethod.class)))
      ), TailType.UNKNOWN);
    }

    if (PsiJavaPatterns.psiElement().inside(PsiAnnotationParameterList.class).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL),
          new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter())
      )), TailType.NONE);
    }

    if (PsiJavaPatterns.psiElement().inside(PsiJavaPatterns.psiElement(PsiVariable.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(
          new AndFilter(valueTypeFilter,
                        new ElementExtractorFilter(new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class))),
                        new ElementExtractorFilter(new ExcludeSillyAssignment())), TailType.NONE);
    }

    return new Pair<ElementFilter, TailType>(
        new AndFilter(valueTypeFilter, new ElementExtractorFilter(new ExcludeSillyAssignment())), TailType.NONE);
  }

  public boolean fillCompletionVariants(final JavaSmartCompletionParameters parameters, final CompletionResultSet result) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiElement element = parameters.getPosition();
        final PsiReference reference = element.getContainingFile().findReferenceAt(parameters.getOffset());
        if (reference != null) {
          final Pair<ElementFilter, TailType> pair = getReferenceFilter(element);
          if (pair != null) {
            final THashSet<LookupItem> set = new THashSet<LookupItem>();
            JavaSmartCompletionContributor.SMART_DATA.completeReference(reference, element, set, pair.second, result.getPrefixMatcher(),
                                                                        parameters.getOriginalFile(), pair.first, new CompletionVariant());
            for (final LookupItem item : set) {
              result.addElement(item);
            }
          }
        }
      }
    });
    return true;
  }
}