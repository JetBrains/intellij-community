// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class BoxingBoxedValueInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NonNls
  static final Map<String, String> boxedPrimitiveMap =
    new HashMap<>(8);

  static {
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_LONG, "long");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "boxing.boxed.value.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new BoxingBoxedValueFix();
  }

  private static class BoxingBoxedValueFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "boxing.boxed.value.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiCallExpression parent = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, PsiNewExpression.class);
      if (parent == null) {
        return;
      }
      parent.replace(element);
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BoxingBoxedValueVisitor();
  }

  private static class BoxingBoxedValueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType constructorType = expression.getType();
      if (constructorType == null) {
        return;
      }
      final String constructorTypeText =
        constructorType.getCanonicalText();
      if (!boxedPrimitiveMap.containsKey(constructorTypeText)) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiParameterList parameterList =
        constructor.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter parameter = parameters[0];
      final PsiType parameterType = parameter.getType();
      final String parameterTypeText = parameterType.getCanonicalText();
      final String boxableConstructorType =
        boxedPrimitiveMap.get(constructorTypeText);
      if (!boxableConstructorType.equals(parameterTypeText)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      final String argumentTypeText = argumentType.getCanonicalText();
      if (!constructorTypeText.equals(argumentTypeText)) {
        return;
      }
      registerError(argument);
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"valueOf".equals(referenceName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (className == null) {
        return;
      }
      if (!boxedPrimitiveMap.containsKey(className)) {
        return;
      }
      if (method.getParameterList().getParametersCount() != 1) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      final String argumentTypeText = argumentType.getCanonicalText();
      if (!className.equals(argumentTypeText)) {
        return;
      }
      registerError(argument);
    }
  }
}
