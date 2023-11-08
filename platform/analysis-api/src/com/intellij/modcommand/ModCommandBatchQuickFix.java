// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Experimental
public abstract class ModCommandBatchQuickFix extends ModCommandQuickFix implements BatchQuickFix {
  /**
   * A method that computes the final action of all the quick-fixes for the supplied descriptors and returns it. 
   * Executed in a background read-action under progress.
   *
   * @param project    {@link Project}
   * @param descriptors problems reported by the tool which provided this quick fix action
   * @return a command to be applied to finally execute the fix.
   */
  public abstract @NotNull ModCommand perform(@NotNull Project project, @NotNull List<ProblemDescriptor> descriptors);

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    return perform(project, List.of(descriptor));
  }

  @Override
  public final void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    List<ProblemDescriptor> descriptorList = ContainerUtil.filterIsInstance(descriptors, ProblemDescriptor.class);
    if (!descriptorList.isEmpty()) {
      ModCommand command = perform(project, descriptorList);
      ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(descriptorList.get(0)), command);
    }
    if (refreshViews != null) {
      refreshViews.run();
    }
  }
}
