// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

final class ReplaceConstructorWithBuilderServiceImpl implements ReplaceConstructorWithBuilderService {

  @Override
  public void showDialog(@NotNull Project project, PsiMethod @NotNull [] constructors) {
    new ReplaceConstructorWithBuilderDialog(project, constructors).show();
  }
}
