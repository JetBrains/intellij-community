// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.*;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class GenericsInspectionToolBase extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (!PsiUtil.isAvailable(JavaFeature.GENERICS, file)) return PsiElementVisitor.EMPTY_VISITOR;

    return super.buildVisitor(holder, isOnTheFly);
  }

  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    if (initializers.length == 0) return null;
    List<ProblemDescriptor> descriptors = new ArrayList<>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] localDescriptions = getDescriptions(initializer, manager, isOnTheFly);
      if (localDescriptions != null) {
        ContainerUtil.addAll(descriptors, localDescriptions);
      }
    }
    if (descriptors.isEmpty()) return null;
    return descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      return getDescriptions(initializer, manager, isOnTheFly);
    }
    if (field instanceof PsiEnumConstant) {
      return getDescriptions(field, manager, isOnTheFly);
    }
    return null;
  }

  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      return getDescriptions(body, manager, isOnTheFly);
    }
    return null;
  }

  public abstract ProblemDescriptor @Nullable [] getDescriptions(@NotNull PsiElement place, @NotNull InspectionManager manager, boolean isOnTheFly);
}
