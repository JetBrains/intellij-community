// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public interface IntroduceConstantHandlerBase {
  void invoke(@NotNull Project project, PsiExpression @NotNull [] expressions);
}
