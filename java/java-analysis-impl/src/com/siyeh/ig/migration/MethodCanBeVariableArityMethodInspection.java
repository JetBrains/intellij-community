// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ConvertToVarargsMethodFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.siyeh.InspectionGadgetsBundle.message;

public final class MethodCanBeVariableArityMethodInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreByteAndShortArrayParameters = false;

  public boolean ignoreAllPrimitiveArrayParameters = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreOverridingMethods = false;

  @SuppressWarnings("PublicField")
  public boolean onlyReportPublicMethods = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreMultipleArrayParameters = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreMultiDimensionalArrayParameters = false;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return message("method.can.be.variable.arity.method.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreByteAndShortArrayParameters", message("method.can.be.variable.arity.method.ignore.byte.short.option"),
               checkbox("ignoreAllPrimitiveArrayParameters", message("method.can.be.variable.arity.method.ignore.all.primitive.arrays.option"))),
      checkbox("ignoreOverridingMethods", message("ignore.methods.overriding.super.method")),
      checkbox("onlyReportPublicMethods", message("only.report.public.methods.option")),
      checkbox("ignoreMultipleArrayParameters", message("method.can.be.variable.arity.method.ignore.multiple.arrays.option")),
      checkbox("ignoreMultiDimensionalArrayParameters",
               message("method.can.be.variable.arity.method.ignore.multidimensional.arrays.option")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ConvertToVarargsMethodFix();
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCanBeVariableArityMethodVisitor();
  }

  private class MethodCanBeVariableArityMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (onlyReportPublicMethods && !method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      if (JavaPsiRecordUtil.isCompactConstructor(method) || JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) {
        return;
      }
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      final PsiType type = lastParameter.getType();
      if (!(type instanceof PsiArrayType arrayType) || type instanceof PsiEllipsisType) {
        return;
      }
      if (NullableNotNullManager.isNullable(lastParameter)) {
        return;
      }
      final PsiType componentType = arrayType.getComponentType();
      if (ignoreMultiDimensionalArrayParameters && componentType instanceof PsiArrayType) {
        // don't report when it is multidimensional array
        return;
      }
      if (ignoreByteAndShortArrayParameters) {
        if (PsiTypes.byteType().equals(componentType) || PsiTypes.shortType().equals(componentType)) {
          return;
        }
        if (ignoreAllPrimitiveArrayParameters && componentType instanceof PsiPrimitiveType) {
          return;
        }
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      if (ignoreOverridingMethods && MethodUtils.hasSuper(method)) {
        return;
      }
      if (ignoreMultipleArrayParameters) {
        for (int i = 0, length = parameters.length - 1; i < length; i++) {
          final PsiParameter parameter = parameters[i];
          if (parameter.getType() instanceof PsiArrayType) {
            return;
          }
        }
      }
      final PsiElement nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      if (isVisibleHighlight(method)) {
        registerErrorAtOffset(method, nameIdentifier.getStartOffsetInParent(), nameIdentifier.getTextLength());
      }
      else {
        final int offset = nameIdentifier.getStartOffsetInParent();
        final int length = parameterList.getStartOffsetInParent() + parameterList.getTextLength() - offset;
        registerErrorAtOffset(method, offset, length);
      }
    }
  }
}
