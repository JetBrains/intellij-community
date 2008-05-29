/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.patterns.StandardPatterns.or;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.getters.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class BasicExpressionCompletionContributor extends ExpressionSmartCompletionContributor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.BasicExpressionCompletionContributor");

  private static void addKeyword(final CompletionResultSet result, final PsiElement element, final String s) {
    try {
      final PsiKeyword keyword = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createKeyword(s);
      result.addElement(
            LookupItemUtil.objectToLookupItem(keyword).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  public BasicExpressionCompletionContributor() {
    extend(PsiJavaPatterns.psiElement().afterLeaf(
        PsiJavaPatterns.psiElement().withText(".").afterLeaf(
            PsiJavaPatterns.psiElement().withParent(
                PsiJavaPatterns.psiElement().referencing(psiClass())))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        addKeyword(result, element, PsiKeyword.CLASS);
        addKeyword(result, element, PsiKeyword.THIS);
      }

    });

    extend(PsiJavaPatterns.psiElement().withSuperParent(2,
                                                        or(
                                                            PsiJavaPatterns.psiElement(PsiConditionalExpression.class).withParent(
                                                                PsiReturnStatement.class),
                                                            PsiJavaPatterns.psiElement(PsiReturnStatement.class))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();

        final PsiElement parent = element.getParent();
        if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifierExpression() != null) return;

        final PsiClass collectionsClass =
            JavaPsiFacade.getInstance(element.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTIONS, element.getResolveScope());
        if (collectionsClass == null) return;

        final PsiType type = parameters.getExpectedType();
        final PsiType defaultType = parameters.getDefaultType();
        addCollectionMethod(result, type, defaultType, CommonClassNames.JAVA_UTIL_LIST, "emptyList", collectionsClass);
        addCollectionMethod(result, type, defaultType, CommonClassNames.JAVA_UTIL_SET, "emptySet", collectionsClass);
        addCollectionMethod(result, type, defaultType, CommonClassNames.JAVA_UTIL_MAP, "emptyMap", collectionsClass);

      }

      private void addCollectionMethod(final CompletionResultSet result, final PsiType expectedType,
                                       final PsiType defaultType, final String baseClassName,
                                       @NonNls final String method, @NotNull final PsiClass collectionsClass) {
        if (isClassType(expectedType, baseClassName) || isClassType(expectedType, CommonClassNames.JAVA_UTIL_COLLECTION) ||
            isClassType(defaultType, baseClassName) || isClassType(defaultType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
          final PsiMethod[] methods = collectionsClass.findMethodsByName(method, false);
          if (methods.length != 0) {
            result.addElement(JavaAwareCompletionData.qualify(
            LookupItemUtil.objectToLookupItem(methods[0]).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE).setTailType(
                TailType.NONE)));
          }
        }
      }

    });

    extend(not(psiElement().afterLeaf(".")), new CompletionProvider<JavaSmartCompletionParameters>() {
      protected void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiType expectedType = parameters.getExpectedType();
        final ClassLiteralGetter classGetter =
            new ClassLiteralGetter(new FilterGetter(new ContextGetter() {
              public Object[] get(final PsiElement context, final CompletionContext completionContext) {
                return new Object[]{expectedType};
              }
            }, new ExcludeDeclaredFilter(new ClassFilter(PsiClass.class))));
        for (final LookupElement<PsiExpression> element : classGetter.get(parameters.getPosition(), null)) {
          result.addElement(element.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
        }

        for (final Object o : new TemplatesGetter().get(parameters.getPosition(), null)) {
          result.addElement(LookupItemUtil.objectToLookupItem(o));
        }

        MembersGetter.addMembers(parameters.getPosition(), expectedType, result);
        if (!parameters.getDefaultType().equals(expectedType)) {
          MembersGetter.addMembers(parameters.getPosition(), parameters.getDefaultType(), result);
        }

        final PsiElement position = parameters.getPosition();
        addKeyword(result, position, PsiKeyword.TRUE);
        addKeyword(result, position, PsiKeyword.FALSE);

        for (final PsiExpression expression : ThisGetter.getThisExpressionVariants(position)) {
          result.addElement(LookupItemUtil.objectToLookupItem(expression));
        }
      }
    });

    final ReferenceExpressionCompletionContributor referenceContributor = new ReferenceExpressionCompletionContributor();
    extend(psiElement(), new CompletionProvider<JavaSmartCompletionParameters>() {
      protected void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        referenceContributor.fillCompletionVariants(parameters, result);
      }
    });

  }

  private static boolean isClassType(final PsiType type, final String className) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      return psiClass != null && className.equals(psiClass.getQualifiedName());
    }
    return false;
  }
}
