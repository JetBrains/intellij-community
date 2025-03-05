// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class SafeVarargsHasNoEffectInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ANNOTATIONS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (PsiUtil.isAccessedForReading(expression)) return;
        if (!(expression.resolve() instanceof PsiParameter parameter)) return;
        if (!(parameter.getDeclarationScope() instanceof PsiMethod method)) return;
        if (!(parameter.getType() instanceof PsiEllipsisType)) return;
        if (!method.getModifierList().hasAnnotation(CommonClassNames.JAVA_LANG_SAFE_VARARGS)) return;
        holder.registerProblem(expression, JavaAnalysisBundle.message("safe.varargs.not.suppress.potentially.unsafe.operations"));
      }
    };
  }
}
