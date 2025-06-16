// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.ui.NewUiValue;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class EmptyIntentionAction extends AbstractEmptyIntentionAction implements LowPriorityAction, Iconable {
  private final @IntentionFamilyName String myName;

  public EmptyIntentionAction(@NotNull @IntentionFamilyName String name) {
    myName = name;
  }

  @Override
  public @NotNull String getText() {
    return AnalysisBundle.message("inspection.options.action.text", myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return myName;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    // edit inspection settings is always enabled
    return true;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EmptyIntentionAction that = (EmptyIntentionAction)o;

    return myName.equals(that.myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return NewUiValue.isEnabled() ? EmptyIcon.ICON_0 : AllIcons.Actions.RealIntentionBulb;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile psiFile) {
    return new IntentionPreviewInfo.Html(AnalysisBundle.message("empty.inspection.action.description", myName));
  }
}
