// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.references;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PomTargetPsiElementImpl;
import com.intellij.pom.PomTarget;
import com.intellij.openapi.project.Project;

public final class PomServiceImpl extends PomService {
  private final Project myProject;

  public PomServiceImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public PsiElement convertToPsi(@NotNull PomTarget target) {
    if (target instanceof PsiElement) {
      return (PsiElement)target;
    }
    return new PomTargetPsiElementImpl(myProject, target);
  }
}
