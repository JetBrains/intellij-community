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

public final class SynchronizeOnValueBasedClassInspection extends LocalInspectionTool {
  private static final @NonNls String ANNOTATION_NAME = "jdk.internal.ValueBased";

  @Override
  public @NotNull String getID() {
    return "synchronization";
  }

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

        final PsiType monitorType = monitor.getType();
        if (monitorType == null) return;

        if (!isValueBasedClass(monitorType)) {
          final TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(monitor));
          final PsiType inferredType = constraint.getPsiType(statement.getProject());

          if (monitorType.equals(inferredType)) return;

          if (!isValueBasedClass(inferredType)) return;
        }

        holder.registerProblem(monitor, JavaBundle.message("inspection.value.based.warnings.synchronization"));
      }
    };
  }

  /**
   * A class is considered a value-based class when it is annotated with <code>jdk.internal.ValueBased</code>.
   * Wherever the annotation is applied to an abstract class or interface, it is also applied to all subclasses in the JDK,
   * so all such subclasses are considered value-based classes
   *
   * @param type type to decide if it's of a value-based class
   * @return true when the argument is of a value-based class
   */
  @Contract(value = "null -> false", pure = true)
  private static boolean isValueBasedClass(PsiType type) {
    final PsiClass classType = PsiTypesUtil.getPsiClass(type);
    if (classType == null) return false;

    if (classType.hasAnnotation(ANNOTATION_NAME)) return true;

    return ContainerUtil.or(type.getSuperTypes(), superType -> isValueBasedClass(superType));
  }
}
