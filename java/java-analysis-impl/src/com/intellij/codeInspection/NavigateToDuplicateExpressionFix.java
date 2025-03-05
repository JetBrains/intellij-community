// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class NavigateToDuplicateExpressionFix extends ModCommandQuickFix {
  private final SmartPsiElementPointer<PsiExpression> myPointer;

   public NavigateToDuplicateExpressionFix(@NotNull PsiExpression arg) {
    myPointer = SmartPointerManager.getInstance(arg.getProject()).createSmartPsiElementPointer(arg);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return JavaBundle.message("navigate.to.duplicate.fix");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression element = myPointer.getElement();
    if (element == null) return ModCommand.nop();
    return ModCommand.select(element);
  }
}
