/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author peter
 */
public class BasicExpressionCompletionContributor extends ExpressionSmartCompletionContributor{

  private static void addKeyword(final CompletionResultSet result, final PsiElement element, final String s) {
    result.addElement(createKeywordLookupItem(element, s));
  }

  public static LookupItem createKeywordLookupItem(final PsiElement element, final String s) {
    return ApplicationManager.getApplication().runReadAction(new Computable<LookupItem>() {
      public LookupItem compute() {
        try {
          final PsiKeyword keyword = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createKeyword(s);
          return LookupItemUtil.objectToLookupItem(keyword).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE);
        }
        catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }
      }
    });
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
        final PsiElement position = parameters.getPosition();
        final PsiType expectedType = parameters.getExpectedType();
        final ClassLiteralGetter classGetter =
            new ClassLiteralGetter(new FilterGetter(new ContextGetter() {
              public Object[] get(final PsiElement context, final CompletionContext completionContext) {
                return new Object[]{expectedType};
              }
            }, new ExcludeDeclaredFilter(new ClassFilter(PsiClass.class))));
        for (final MutableLookupElement<PsiExpression> element : classGetter.getClassLiterals(position, null, result.getPrefixMatcher())) {
          result.addElement(element.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
        }

        for (final Object o : new TemplatesGetter().get(position, null)) {
          result.addElement(LookupItemUtil.objectToLookupItem(o));
        }

        addKeyword(result, position, PsiKeyword.TRUE);
        addKeyword(result, position, PsiKeyword.FALSE);

        final PsiElement parent = position.getParent();
        if (parent != null && !(parent.getParent() instanceof PsiSwitchLabelStatement)) {
          MembersGetter.addMembers(parameters.getPosition(), expectedType, result);
          if (!parameters.getDefaultType().equals(expectedType)) {
            MembersGetter.addMembers(parameters.getPosition(), parameters.getDefaultType(), result);
          }

          for (final PsiExpression expression : ThisGetter.getThisExpressionVariants(position)) {
            result.addElement(LookupItemUtil.objectToLookupItem(expression));
          }
        }

        processDataflowExpressionTypes(position, expectedType, new Consumer<CastingLookupElementDecorator>() {
          public void consume(CastingLookupElementDecorator castingLookupElementDecorator) {
            result.addElement(castingLookupElementDecorator);
          }
        });
      }
    });

    final ReferenceExpressionCompletionContributor referenceContributor = new ReferenceExpressionCompletionContributor();
    extend(psiElement(), new CompletionProvider<JavaSmartCompletionParameters>() {
      protected void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        referenceContributor.fillCompletionVariants(parameters, result);
      }
    });

  }

  public static void processDataflowExpressionTypes(PsiElement position, @Nullable PsiType expectedType, Consumer<CastingLookupElementDecorator> consumer) {
    final PsiExpression context = PsiTreeUtil.getParentOfType(position, PsiExpression.class);
    if (context == null) return;

    final Map<PsiExpression,PsiType> map = GuessManager.getInstance(position.getProject()).getDataFlowExpressionTypes(context);
    for (final PsiExpression expression : map.keySet()) {
      final PsiType castType = map.get(expression);
      final PsiType baseType = expression.getType();
      if (expectedType == null || (expectedType.isAssignableFrom(castType) && (baseType == null || !expectedType.isAssignableFrom(baseType)))) {
        consumer.consume(new CastingLookupElementDecorator(expressionToLookupElement(expression), castType));
      }
    }
  }

  @NotNull
  private static LookupElement expressionToLookupElement(@NotNull PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)expression;
      if (!refExpr.isQualified()) {
        final PsiElement target = refExpr.resolve();
        if (target instanceof PsiVariable) {
          final LookupItem item = LookupItemUtil.objectToLookupItem(target);
          item.setAttribute(LookupItem.SUBSTITUTOR, PsiSubstitutor.EMPTY);
          return item;
        }
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (!call.getMethodExpression().isQualified()) {
        final PsiMethod method = call.resolveMethod();
        if (method != null) {
          return new JavaMethodCallElement(method);
        }
      }
    }

    return new ExpressionLookupItem(expression);
  }

  private static boolean isClassType(final PsiType type, final String className) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      return psiClass != null && className.equals(psiClass.getQualifiedName());
    }
    return false;
  }
}
