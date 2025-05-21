// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * A simple adapter of {@link IntentionAction} to {@link LocalQuickFix}.
 * Mostly necessary for outdated code. In new code, it's preferred to use {@link com.intellij.modcommand.ModCommandAction}
 * which could be adapted using {@link LocalQuickFix#from(ModCommandAction)}.
 */
public class LocalQuickFixBackedByIntentionAction implements LocalQuickFix, Iconable, ReportingClassSubstitutor {
  private final @NotNull IntentionAction myAction;

  public LocalQuickFixBackedByIntentionAction(@NotNull IntentionAction action) {
    myAction = action;
  }

  @Override
  public @NotNull String getName() {
    return myAction.getText();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    myAction.invoke(project, null, getPsiFile(descriptor));
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return myAction.generatePreview(project,
                                    Objects.requireNonNull(IntentionPreviewUtils.getPreviewEditor()),
                                    Objects.requireNonNull(getPsiFile(previewDescriptor)));
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myAction.getElementToMakeWritable(file);
  }

  @Override
  public @NotNull Class<?> getSubstitutedClass() {
    return ReportingClassSubstitutor.getClassToReport(myAction);
  }

  @Override
  public boolean startInWriteAction() {
    return myAction.startInWriteAction();
  }

  private static @Nullable PsiFile getPsiFile(@NotNull ProblemDescriptor descriptor) {
    PsiElement startElement = descriptor.getStartElement();
    if (startElement != null) {
      return startElement.getContainingFile();
    }
    PsiElement endElement = descriptor.getEndElement();
    if (endElement != null) {
      return endElement.getContainingFile();
    }
    return null;
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    if (myAction instanceof Iconable iconable) {
      return iconable.getIcon(flags);
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocalQuickFixBackedByIntentionAction action = (LocalQuickFixBackedByIntentionAction)o;

    return myAction.equals(action.myAction);
  }

  @Override
  public int hashCode() {
    return myAction.hashCode();
  }
}
