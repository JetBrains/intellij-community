// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public abstract class PsiUpdateModCommandQuickFix extends ModCommandQuickFix {
  @Override
  public final @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    return ModCommands.psiUpdate(descriptor.getStartElement(), (e, updater) -> applyFix(project, e, updater));
  }

  protected abstract void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull EditorUpdater updater);
}
