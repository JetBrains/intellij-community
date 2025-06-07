// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;

public class RemoveRedundantParameterTypesFix extends PsiUpdateModCommandAction<PsiLambdaExpression> {
  public RemoveRedundantParameterTypesFix(@NotNull PsiLambdaExpression lambdaExpression) {
    super(lambdaExpression);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("quickfix.family.remove.redundant.parameter.types");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiLambdaExpression lambda, @NotNull ModPsiUpdater updater) {
    removeLambdaParameterTypesIfPossible(lambda);
  }

  public static boolean isApplicable(@NotNull PsiParameterList parameterList) {
    final PsiElement parent = parameterList.getParent();
    if (!(parent instanceof PsiLambdaExpression expression)) return false;
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) return false;
      if (!PsiUtil.isAvailable(JavaFeature.VAR_LAMBDA_PARAMETER, parameterList)) {
        if (AnonymousCanBeLambdaInspection.mustKeepAnnotations(parameter, Collections.emptySet())) {
          return false;
        }
      }
      else if (typeElement.isInferredType() && keepVarType(parameter)) return false;
    }
    if (parameters.length == 0) return false;
    final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType == null) return false;
    return LambdaUtil.isSafeLambdaReplacement(expression, () -> {
      PsiLambdaExpression lambdaWithoutParameters = (PsiLambdaExpression)expression.copy();
      for (PsiParameter parameter : lambdaWithoutParameters.getParameterList().getParameters()) {
        PsiTypeElement typeElement = Objects.requireNonNull(parameter.getTypeElement());
        typeElement.delete();
      }
      return lambdaWithoutParameters;
    });
  }

  /**
   * Removes lambda parameter types when possible
   *
   * @param lambdaExpression lambda expression to process
   */
  public static void removeLambdaParameterTypesIfPossible(@NotNull PsiLambdaExpression lambdaExpression) {
    PsiParameterList list = lambdaExpression.getParameterList();
    if (isApplicable(list)) {
      removeTypes(lambdaExpression);
    }
  }

  private static void removeTypes(PsiLambdaExpression lambdaExpression) {
    if (lambdaExpression != null) {
      final PsiParameter[] parameters = lambdaExpression.getParameterList().getParameters();
      if (PsiUtil.isAvailable(JavaFeature.VAR_LAMBDA_PARAMETER, lambdaExpression) &&
          ContainerUtil.exists(parameters, parameter -> keepVarType(parameter))) {
        for (PsiParameter parameter : parameters) {
          PsiTypeElement element = parameter.getTypeElement();
          if (element != null) {
            new CommentTracker().replaceAndRestoreComments(element, JavaKeywords.VAR);
          }
        }
        return;
      }
      final String text;
      if (parameters.length == 1) {
        text = parameters[0].getName();
      }
      else {
        text = "(" + StringUtil.join(parameters, PsiParameter::getName, ", ") + ")";
      }
      final PsiLambdaExpression expression = (PsiLambdaExpression)JavaPsiFacade.getElementFactory(lambdaExpression.getProject())
        .createExpressionFromText(text + "->{}", lambdaExpression);
      CommentTracker tracker = new CommentTracker();
      tracker.replaceAndRestoreComments(lambdaExpression.getParameterList(), expression.getParameterList());
    }
  }

  private static boolean keepVarType(PsiParameter parameter) {
    return parameter.hasModifierProperty(PsiModifier.FINAL) || parameter.getAnnotations().length > 0;
  }
}
