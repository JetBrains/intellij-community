// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class ConvertToVarargsMethodFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("convert.to.variable.arity.method.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiMethod method)) {
      return;
    }
    final Collection<PsiReferenceExpression> methodCalls = new ArrayList<>();
    for (final PsiReference reference : ReferencesSearch.search(method, method.getUseScope(), false).asIterable()) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiReferenceExpression ref) {
        methodCalls.add(updater.getWritable(ref));
      }
    }
    method = updater.getWritable(method);
    makeMethodVarargs(method);
    makeMethodCallsVarargs(methodCalls);
  }

  private static void makeMethodVarargs(PsiMethod method) {
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.isEmpty()) {
      return;
    }
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiParameter lastParameter = parameters[parameters.length - 1];
    lastParameter.normalizeDeclaration();
    final PsiType type = lastParameter.getType();
    if (!(type instanceof PsiArrayType arrayType)) {
      return;
    }
    final PsiType componentType = arrayType.getComponentType();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    final PsiType ellipsisType = new PsiEllipsisType(componentType, TypeAnnotationProvider.Static.create(type.getAnnotations()));
    final PsiTypeElement newTypeElement = factory.createTypeElement(ellipsisType);
    final PsiTypeElement typeElement = lastParameter.getTypeElement();
    if (typeElement != null) {
      CommentTracker ct = new CommentTracker();
      ct.grabComments(typeElement);
      PsiElement result = typeElement.replace(newTypeElement);
      ct.insertCommentsBefore(result.getLastChild()); // Swap comments, example: String /* Foo */ [] -> String/* Foo */...
    }
  }

  private static void makeMethodCallsVarargs(Collection<PsiReferenceExpression> referenceExpressions) {
    for (final PsiReferenceExpression referenceExpression : referenceExpressions) {
      final PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiMethodCallExpression methodCallExpression)) {
        continue;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        continue;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      if (!(lastArgument instanceof PsiNewExpression newExpression)) {
        continue;
      }
      final PsiArrayInitializerExpression arrayInitializerExpression = newExpression.getArrayInitializer();
      if (arrayInitializerExpression == null) {
        continue;
      }
      final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
      for (final PsiExpression initializer : initializers) {
        argumentList.add(initializer);
      }
      lastArgument.delete();
    }
  }
}
