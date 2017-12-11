// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.references;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PomService {

  private static PomService getInstance(Project project) {
    return ServiceManager.getService(project, PomService.class);
  }

  @NotNull
  protected abstract PsiElement convertToPsi(@NotNull PomTarget target);

  public static PsiElement convertToPsi(@NotNull Project project, @NotNull PomTarget target) {
    return getInstance(project).convertToPsi(target);
  }

  public static PsiElement convertToPsi(@NotNull PsiTarget target) {
    return getInstance(target.getNavigationElement().getProject()).convertToPsi((PomTarget)target);
  }

  @NotNull
  public static PomTarget convertToPom(@NotNull PsiElement element) {
    if (element instanceof PomTargetPsiElement) {
      return ((PomTargetPsiElement)element).getTarget();
    }
    else if (element instanceof PomTarget) {
      return (PomTarget)element;
    }
    else {
      return new DelegatePsiTarget(element);
    }
  }
}
