/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.patterns.StandardPatterns.string;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.GeneratorFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.ThrowsListGetter;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public class AnywhereSmartCompletionContributor extends JavaSmartCompletionContributor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.AnywhereSmartCompletionContributor");

  public AnywhereSmartCompletionContributor() {
    final GeneratorFilter filter = new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter());
    extend(PsiJavaPatterns.psiElement().afterLeaf(
        PsiJavaPatterns.psiElement().withText(".").afterLeaf(
            PsiJavaPatterns.psiElement().withParent(
                PsiJavaPatterns.psiElement().referencing(psiClass())))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        try {
          final PsiElement element = parameters.getPosition();
          addKeyword(result, element, filter, PsiKeyword.CLASS);
          addKeyword(result, element, filter, PsiKeyword.THIS);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      private void addKeyword(final CompletionResultSet result, final PsiElement element, final GeneratorFilter filter, final String s)
          throws IncorrectOperationException {
        final PsiKeyword keyword = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createKeyword(s);
        if (filter.isAcceptable(keyword, element)) {
          result.addElement(
              LookupItemUtil.objectToLookupItem(keyword).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE));
        }
      }

    });

    extend(PsiJavaPatterns.psiElement().inside(
        PsiJavaPatterns.psiElement(PsiDocTag.class).withName(
            string().oneOf(PsiKeyword.THROWS, JavaSmartCompletionData.EXCEPTION_TAG))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        for (final Object object : new ThrowsListGetter().get(element, completionContext)) {
          result.addElement(LookupItemUtil.objectToLookupItem(object).setTailType(TailType.SPACE));
        }
      }
    });

    extend(PsiJavaPatterns.psiElement().withSuperParent(2,
                                                                              or(
                                                                                  PsiJavaPatterns.psiElement(PsiConditionalExpression.class).withParent(
                                                                                      PsiReturnStatement.class),
                                                                                  PsiJavaPatterns.psiElement(PsiReturnStatement.class))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final ContextGetter chooser = new JavaSmartCompletionData.EmptyCollectionGetter();
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        for (final Object object : chooser.get(element, completionContext)) {
          result.addElement(JavaAwareCompletionData.qualify(
              LookupItemUtil.objectToLookupItem(object).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE).setTailType(
                  TailType.NONE)));
        }
      }
    });

    extend(or(
        PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class),
        PsiJavaPatterns.psiElement().withSuperParent(2, PsiNameValuePair.class)), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final ContextGetter chooser = new ContextGetter() {
          public Object[] get(PsiElement element, CompletionContext context) {
            final Set<Object> result = new THashSet<Object>();
            final ExpectedTypesGetter expectedTypesGetter = new ExpectedTypesGetter();
            final Object[] objects = expectedTypesGetter.get(element, context);

            if (objects != null) {
              for (final Object object : objects) {
                if (object instanceof PsiType) {
                  PsiType type = (PsiType)object;
                  if (type instanceof PsiArrayType) {
                    type = ((PsiArrayType)type).getComponentType();
                  }

                  if (type instanceof PsiClassType) {
                    final PsiClass psiClass = ((PsiClassType)type).resolve();
                    if (psiClass != null && psiClass.isAnnotationType()) {
                      result.add(psiClass);
                    }
                  }
                }
              }
            }
            return result.toArray(new Object[result.size()]);
          }
        };
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final ElementPattern<? extends PsiElement> leftNeighbor = PsiJavaPatterns.psiElement().afterLeaf(PsiJavaPatterns.psiElement().withText("."));
        final boolean needQualify = leftNeighbor.accepts(element);

        for (final Object object : chooser.get(element, completionContext)) {
          final LookupItem item = LookupItemUtil.objectToLookupItem(object).setTailType(TailType.NONE);
          if (needQualify) JavaAwareCompletionData.qualify(item);
          result.addElement(item);
        }
      }
    });
  }
}
