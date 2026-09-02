// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Reports a not-null parameter that a call site receives the {@code null} literal for.
 * <p>
 * The check searches the whole project for such a call site, so it runs in the editor only.
 */
public final class NotNullParameterReceivesNullInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    // The check needs a project-wide search for the 'null' arguments. It is too slow for the batch mode.
    if (!isOnTheFly) return PsiElementVisitor.EMPTY_VISITOR;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(holder.getProject());
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        checkParameters(method, holder, manager);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (!aClass.isRecord()) return;
        PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
        if (constructor instanceof SyntheticElement) {
          checkParameters(constructor, holder, manager);
        }
      }
    };
  }

  private static void checkParameters(@NotNull PsiMethod method,
                                      @NotNull ProblemsHolder holder,
                                      @NotNull NullableNotNullManager manager) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (parameter.getType() instanceof PsiPrimitiveType) continue;
      checkParameter(method, holder, manager, i, parameter);
    }
  }

  private static void checkParameter(@NotNull PsiMethod method,
                                     @NotNull ProblemsHolder holder,
                                     @NotNull NullableNotNullManager manager,
                                     int parameterIdx,
                                     @NotNull PsiParameter parameter) {
    PsiVariable owner = parameter.isPhysical() ? parameter : JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(parameter);
    if (owner == null) return;

    PsiElement elementToHighlight = null;
    NullabilityAnnotationInfo info = manager.findOwnNullabilityInfo(owner);
    if (info != null && !info.isInferred()) {
      if (info.getNullability() == Nullability.NOT_NULL) {
        PsiAnnotation notNullAnnotation = info.getAnnotation();
        boolean physical = PsiTreeUtil.isAncestor(owner, notNullAnnotation, true);
        elementToHighlight = physical ? notNullAnnotation : owner.getNameIdentifier();
      }
    }
    else {
      info = DfaPsiUtil.getTypeNullabilityInfo(owner.getType());
      if (info != null && info.getNullability() == Nullability.NOT_NULL) {
        elementToHighlight = owner.getNameIdentifier();
      }
    }
    if (elementToHighlight == null || !JavaNullMethodArgumentUtil.hasNullArgument(method, parameterIdx)) return;

    String annotationName = StringUtil.getShortName(requireNonNull(info.getAnnotation().getQualifiedName()));
    holder.problem(elementToHighlight,
                   JavaAnalysisBundle.message("inspection.nullable.problems.NotNull.parameter.receives.null.literal", annotationName))
      .fix(new NavigateToNullLiteralArguments(parameter))
      .register();
  }
}
