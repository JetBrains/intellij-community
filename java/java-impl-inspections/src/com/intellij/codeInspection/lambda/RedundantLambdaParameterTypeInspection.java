// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lambda;

import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;

public class RedundantLambdaParameterTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(RedundantLambdaParameterTypeInspection.class);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitParameterList(PsiParameterList parameterList) {
        super.visitParameterList(parameterList);
        if (isApplicable(parameterList)) {
          holder.registerProblem(parameterList, JavaBundle.message("inspection.message.lambda.parameter.type.is.redundant"), new LambdaParametersFix());
        }
      }
    };
  }

  private static boolean isApplicable(@NotNull PsiParameterList parameterList) {
    final PsiElement parent = parameterList.getParent();
    if (!(parent instanceof PsiLambdaExpression)) return false;
    final PsiLambdaExpression expression = (PsiLambdaExpression)parent;
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) return false;
      if (!PsiUtil.isLanguageLevel11OrHigher(parameterList)) {
        if (AnonymousCanBeLambdaInspection.hasRuntimeAnnotations(parameter, Collections.emptySet())) {
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
      if (PsiUtil.isLanguageLevel11OrHigher(lambdaExpression) &&
          ContainerUtil.exists(parameters, parameter -> keepVarType(parameter))) {
        for (PsiParameter parameter : parameters) {
          PsiTypeElement element = parameter.getTypeElement();
          if (element != null) {
            new CommentTracker().replaceAndRestoreComments(element, PsiKeyword.VAR);
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

  private static class LambdaParametersFix implements LocalQuickFix {
    @Nls
    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.remove.redundant.parameter.types");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiLambdaExpression) {
        removeTypes((PsiLambdaExpression)parent);
      }
    }
  }
}
