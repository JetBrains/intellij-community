// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class StringExpressionHelper {

  @Nullable
  public static Pair<PsiElement, String> evaluateExpression(@NotNull PsiElement expression) {
    return evaluateExpression(expression, new HashSet<>());
  }

  @Nullable
  public static Pair<PsiElement, String> evaluateExpression(@NotNull PsiElement expression, @NotNull Collection<PsiElement> visited) {
    if (!visited.add(expression)) {
      return null;
    }

    if (expression instanceof PsiLiteralExpression) {
      return evaluatePsiLiteralExpression(expression);
    }

    if (expression instanceof PsiReferenceExpression) {
      PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
      if (resolve instanceof PsiVariable) {
        PsiExpression initializer = ((PsiVariable)resolve).getInitializer();
        if (initializer != null) {
          Pair<PsiElement, String> expr = evaluateExpression(initializer, visited);
          if (expr != null) return expr;
        }
      }
    }

    if (expression instanceof PsiMethodCallExpression) {
      final PsiElement element = ((PsiMethodCallExpression)expression).getMethodExpression().resolve();
      if (element instanceof PsiMethod) {
        PsiCodeBlock body = ((PsiMethod)element).getBody();
        if (body != null) {
          final Set<PsiExpression> returns = new HashSet<>();

          body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(PsiClass aClass) {}

            @Override
            public void visitLambdaExpression(PsiLambdaExpression expression) {}

            @Override
            public void visitReturnStatement(PsiReturnStatement statement) {
              PsiExpression returnValue = statement.getReturnValue();
              if (returnValue != null) {
                returns.add(returnValue);
              }
            }
          });

          for (PsiExpression psiExpression : returns) {
            Pair<PsiElement, String> pair = evaluateExpression(psiExpression, visited);
            if (pair != null) {
              return pair;
            }
          }
        }

        return evaluateExpression(element, visited);
      }
      return null;
    }

    Pair<PsiElement, String> constantExpression = evaluateConstantExpression(expression);
    if (constantExpression != null) {
      return constantExpression;
    }

    if (expression instanceof PsiBinaryExpression) {
      // a="a"; b="b"  return a+b;
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      return evaluatePolyadicExpressions(expression, visited, binaryExpression.getLOperand(), binaryExpression.getROperand());
    }
    if (expression instanceof PsiPolyadicExpression) {
      // a="a"; b="b"; c="c"  return a+b+c;
      return evaluatePolyadicExpressions(expression, visited, ((PsiPolyadicExpression)expression).getOperands());
    }


    Collection<? extends PsiElement> elements = DfaUtil.getPossibleInitializationElements(expression);
    for (PsiElement element : elements) {
      Pair<PsiElement, String> expr = evaluateExpression(element, visited);
      if (expr != null) return expr;
    }

    return null;
  }

  @Nullable
  private static Pair<PsiElement, String> evaluatePolyadicExpressions(@NotNull PsiElement expression,
                                                                      @NotNull Collection<PsiElement> visited,
                                                                      PsiExpression @NotNull ... operands) {
    StringBuilder sb = new StringBuilder();
    for (PsiExpression operand : operands) {
      Pair<PsiElement, String> pair = evaluateExpression(operand, visited);
      if (pair == null) return null;
      sb.append(pair.second);
    }
    return Pair.create(expression, sb.toString());
  }

  @NotNull
  private static Pair<PsiElement, String> evaluatePsiLiteralExpression(@NotNull PsiElement expression) {
    return Pair.create(expression, ElementManipulators.getValueText(expression));
  }

  @Nullable
  public static Pair<PsiElement, String> evaluateConstantExpression(@NotNull PsiElement expression) {
    PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper();
    Object result = helper.computeConstantExpression(expression);
    if (result instanceof String) {
      return Pair.create(expression, (String)result);
    }
    return null;
  }

  @NotNull
  public static Set<Pair<PsiElement, String>> searchStringExpressions(@NotNull final PsiMethod psiMethod,
                                                                      @NotNull SearchScope searchScope,
                                                                      int expNum) {
    Set<Pair<PsiElement, String>> pairs = new HashSet<>();
    for (PsiCall methodCallExpression : searchMethodCalls(psiMethod, searchScope)) {
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList == null) continue;
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length > expNum) {
        final PsiExpression expression = expressions[expNum];
        Pair<PsiElement, String> pair = evaluateExpression(expression);
        if (pair != null) {
          pairs.add(pair);
        }
      }
    }

    return pairs;
  }

  @NotNull
  public static Set<PsiCall> searchMethodCalls(@NotNull final PsiMethod psiMethod, @NotNull SearchScope searchScope) {
    final Set<PsiCall> callExpressions = new HashSet<>();
    final CommonProcessors.CollectUniquesProcessor<PsiReference> consumer = new CommonProcessors.CollectUniquesProcessor<>();

    MethodReferencesSearch.search(psiMethod, searchScope, true).forEach(consumer);

    for (PsiReference psiReference : consumer.getResults()) {
      final PsiCall methodCallExpression = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiCall.class);

      if (methodCallExpression != null) {
        callExpressions.add(methodCallExpression);
      }
    }


    return callExpressions;
  }
}
