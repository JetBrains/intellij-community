// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.StreamApiUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class StreamFilterNotNullFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("inspection.data.flow.filter.notnull.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiFunctionalExpression function = findFunction(element);
    if (function == null) return;
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(function, PsiMethodCallExpression.class);
    if (call == null) return;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return;
    String name = suggestVariableName(function, qualifier);
    // We create first lambda, then convert to method reference as user code style might be set to prefer lambdas
    PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
      .createExpressionFromText(qualifier.getText() + ".filter(" + name + "->" + name + "!=null)", qualifier);
    PsiMethodCallExpression result = (PsiMethodCallExpression)qualifier.replace(replacement);
    LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result.getArgumentList());
  }

  @NotNull
  private static String suggestVariableName(@NotNull PsiFunctionalExpression function, @NotNull PsiExpression qualifier) {
    String name = null;
    if (function instanceof PsiLambdaExpression) {
      PsiParameter parameter = ArrayUtil.getFirstElement(((PsiLambdaExpression)function).getParameterList().getParameters());
      if (parameter != null) {
        name = parameter.getName();
      }
    }
    PsiType type = StreamApiUtil.getStreamElementType(qualifier.getType());
    return new VariableNameGenerator(qualifier, VariableKind.PARAMETER).byName(name).byType(type).byName("obj").generate(false);
  }

  @Nullable
  private static PsiFunctionalExpression findFunction(PsiElement reference) {
    if (reference instanceof PsiFunctionalExpression) {
      return (PsiFunctionalExpression)reference;
    }
    if (reference instanceof PsiIdentifier) {
      // in "str.trim()" go from "trim" to "str"
      reference = reference.getParent();
      if (reference instanceof PsiReferenceExpression) {
        reference = PsiUtil.skipParenthesizedExprDown(((PsiReferenceExpression)reference).getQualifierExpression());
      }
    }
    if (reference instanceof PsiReferenceExpression) {
      PsiParameter parameter = tryCast(((PsiReferenceExpression)reference).resolve(), PsiParameter.class);
      if (parameter == null) return null;
      PsiParameterList parameterList = tryCast(parameter.getParent(), PsiParameterList.class);
      if (parameterList == null || parameterList.getParametersCount() != 1) return null;
      return tryCast(parameterList.getParent(), PsiLambdaExpression.class);
    }
    return null;
  }

  public static StreamFilterNotNullFix makeFix(PsiElement reference) {
    PsiFunctionalExpression fn = findFunction(reference);
    if (fn == null) return null;
    PsiExpressionList args = tryCast(PsiUtil.skipParenthesizedExprUp(fn.getParent()), PsiExpressionList.class);
    if (args == null || args.getExpressionCount() != 1) return null;
    PsiMethodCallExpression call = tryCast(args.getParent(), PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null || !InheritanceUtil.isInheritor(qualifier.getType(), CommonClassNames.JAVA_UTIL_STREAM_STREAM)) return null;
    return new StreamFilterNotNullFix();
  }
}
