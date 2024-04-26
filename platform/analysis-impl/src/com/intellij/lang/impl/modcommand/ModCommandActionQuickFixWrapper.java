// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class ModCommandActionQuickFixWrapper extends ModCommandQuickFix implements ReportingClassSubstitutor {
  private final ModCommandAction myAction;

  ModCommandActionQuickFixWrapper(@NotNull ModCommandAction action) {
    myAction = action;
  }

  ModCommandAction getAction() {
    return myAction;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    return myAction.perform(ActionContext.from(descriptor));
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return myAction.generatePreview(ActionContext.from(previewDescriptor));
  }

  @Override
  public @NotNull String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public @NotNull Class<?> getSubstitutedClass() {
    return ReportingClassSubstitutor.getClassToReport(myAction);
  }

  @Override
  public String toString() {
    return "ModCommandActionQuickFixWrapper[action=" + myAction + "]";
  }
}
