// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertThisFix extends InsertConstructorCallFix {

  public InsertThisFix(@NotNull PsiMethod constructor) {
    super(constructor, "this();");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod constructor) {
    if (!hasConstructorToDelegate(constructor)) return null;
    return super.getPresentation(context, constructor);
  }

  private static boolean hasConstructorToDelegate(@NotNull PsiMethod ctor) {
    PsiClass containingClass = ctor.getContainingClass();
    if (containingClass == null) return false;
    return ContainerUtil.exists(containingClass.getConstructors(), constructor -> constructor != ctor);
  }
}
