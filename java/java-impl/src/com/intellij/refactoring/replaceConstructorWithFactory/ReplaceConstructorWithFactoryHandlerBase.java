// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public interface ReplaceConstructorWithFactoryHandlerBase {
  void invokeForPreview(@NotNull Project project, @NotNull PsiClass aClass);

  void invokeForPreview(@NotNull Project project, @NotNull PsiMethod method);
}
