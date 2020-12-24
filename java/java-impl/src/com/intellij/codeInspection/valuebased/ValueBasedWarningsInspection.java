// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.valuebased;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ValueBasedWarningsInspection extends LocalInspectionTool {
  private static final @NonNls String ANNOTATION_NAME = "jdk.internal.ValueBased";

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel16OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitSynchronizedStatement(@NotNull final PsiSynchronizedStatement statement) {
        final PsiExpression monitor = statement.getLockExpression();
        if (monitor == null) return;

        final TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(monitor));

        final PsiType type = constraint.getPsiType(statement.getProject());

        if (!extendsValueBasedClass(type)) return;

        holder.registerProblem(monitor, JavaBundle.message("inspection.value.based.warnings.synchronization"));
      }
    };
  }

  @Contract(value = "null -> false", pure = true)
  private static boolean extendsValueBasedClass(PsiType type) {
    final PsiClass classType = PsiTypesUtil.getPsiClass(type);
    if (classType == null) return false;

    if (classType.hasAnnotation(ANNOTATION_NAME)) return true;

    return ContainerUtil.or(type.getSuperTypes(), superType -> extendsValueBasedClass(superType));
  }
}
