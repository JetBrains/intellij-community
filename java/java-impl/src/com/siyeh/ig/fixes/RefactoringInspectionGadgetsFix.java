// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.RefactoringQuickFix;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 * @deprecated Should not be used, as the whole {@link InspectionGadgetsFix} hierarchy. Use {@link LocalQuickFix} directly.
 */
@Deprecated
public abstract class RefactoringInspectionGadgetsFix extends InspectionGadgetsFix implements RefactoringQuickFix {

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    doFix(descriptor.getPsiElement());
  }
}
