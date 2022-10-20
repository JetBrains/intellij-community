// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.references;

import com.intellij.openapi.project.Project;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTarget;
import org.jetbrains.annotations.NotNull;

public abstract class PomService {

  private static PomService getInstance(Project project) {
    return project.getService(PomService.class);
  }

  @NotNull
  protected abstract PsiElement convertToPsi(@NotNull PomTarget target);

  @NotNull
  public static PsiElement convertToPsi(@NotNull Project project, @NotNull PomTarget target) {
    return getInstance(project).convertToPsi(target);
  }

  @NotNull
  public static PsiElement convertToPsi(@NotNull PsiTarget target) {
    return getInstance(target.getNavigationElement().getProject()).convertToPsi((PomTarget)target);
  }
}
