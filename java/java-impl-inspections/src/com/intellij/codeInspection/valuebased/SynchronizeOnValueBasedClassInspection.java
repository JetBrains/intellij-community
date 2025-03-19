// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.valuebased;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class SynchronizeOnValueBasedClassInspection extends LocalInspectionTool {
  private static final @NonNls String JDK_INTERNAL_VALUE_BASED = "jdk.internal.ValueBased";

  @Override
  public @NotNull String getID() {
    return "synchronization";
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_16)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
        final PsiExpression monitor = statement.getLockExpression();
        if (monitor == null) return;
        final PsiType monitorType = monitor.getType();
        if (monitorType == null) return;

        if (!isValueBasedClass(PsiUtil.resolveClassInClassTypeOnly(monitorType))) {
          final TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(monitor));
          final PsiType inferredType = constraint.getPsiType(statement.getProject());

          if (monitorType.equals(inferredType) || !isValueBasedClass(PsiUtil.resolveClassInClassTypeOnly(inferredType))) return;
        }

        ProblemHighlightType highlightType = JavaFeature.VALHALLA_VALUE_CLASSES.isSufficient(PsiUtil.getLanguageLevel(monitor))
                                             ? ProblemHighlightType.GENERIC_ERROR
                                             : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        holder.registerProblem(monitor, JavaBundle.message("inspection.value.based.warnings.synchronization"), highlightType);
      }
    };
  }

  /**
   * A class is considered a value-based class when it is annotated with <code>jdk.internal.ValueBased</code> or has the Valhalla
   * {@code value} modifier.
   * Wherever the annotation is applied to an abstract class or interface, it is also applied to all subclasses in the JDK,
   * so all such subclasses are considered value-based classes.
   *
   * @param aClass  the class to check if it is a value-based class
   * @return true when the argument is a value-based class
   */
  @Contract(value = "null -> false", pure = true)
  private static boolean isValueBasedClass(PsiClass aClass) {
    if (aClass == null) return false;
    return aClass.isValueClass() ||
           aClass.hasAnnotation(JDK_INTERNAL_VALUE_BASED) ||
           ContainerUtil.or(aClass.getSupers(), c -> isValueBasedClass(c));
  }
}
