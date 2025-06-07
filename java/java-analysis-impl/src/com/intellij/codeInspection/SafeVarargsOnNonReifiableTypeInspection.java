// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

public final class SafeVarargsOnNonReifiableTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ANNOTATIONS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitParameter(@NotNull PsiParameter parameter) {
        super.visitParameter(parameter);
        if (!parameter.isVarArgs()) return;
        if (!(parameter.getDeclarationScope() instanceof PsiMethod method)) return;
        if (!method.getModifierList().hasAnnotation(CommonClassNames.JAVA_LANG_SAFE_VARARGS)) return;
        if (!method.isVarArgs() || !GenericsUtil.isSafeVarargsNoOverridingCondition(method)) return;
        PsiEllipsisType ellipsisType = (PsiEllipsisType)parameter.getType();
        PsiType componentType = ellipsisType.getComponentType();
        if (JavaGenericsUtil.isReifiableType(componentType)) {
          PsiTypeElement element = Objects.requireNonNull(parameter.getTypeElement());
          holder.registerProblem(element, JavaAnalysisBundle.message("safe.varargs.on.reifiable.type"));
        }
      }
    };
  }
}
