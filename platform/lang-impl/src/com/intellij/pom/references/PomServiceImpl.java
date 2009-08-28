/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.references;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PomTargetPsiElementImpl;
import com.intellij.pom.PomTarget;
import com.intellij.openapi.project.Project;

/**
 * @author peter
 */
public class PomServiceImpl extends PomService {
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
