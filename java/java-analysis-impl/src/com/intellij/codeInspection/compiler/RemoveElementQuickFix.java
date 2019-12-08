// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.compiler;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link com.intellij.codeInsight.intention.QuickFixFactory#createDeleteFix} */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public class RemoveElementQuickFix implements LocalQuickFix {
  private final String myName;

  public RemoveElementQuickFix(@NotNull @Nls final String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myName;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element != null) {
      element.delete();
    }
  }
}