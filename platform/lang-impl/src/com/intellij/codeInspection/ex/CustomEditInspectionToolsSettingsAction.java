// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CustomEditInspectionToolsSettingsAction implements IntentionAction, Iconable {
  private final EditInspectionToolsSettingsAction myEditInspectionToolsSettingsAction;   // we delegate due to priority
  private final HighlightDisplayKey myDisplayKey;
  private final Computable<@IntentionName String> myText;

  public CustomEditInspectionToolsSettingsAction(HighlightDisplayKey displayKey, Computable<@IntentionName String> text) {
    myEditInspectionToolsSettingsAction = new EditInspectionToolsSettingsAction(displayKey);
    myDisplayKey = displayKey;
    myText = text;
  }

  @Override
  public @NotNull String getText() {
    return myText.compute();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myEditInspectionToolsSettingsAction.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myEditInspectionToolsSettingsAction.isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myEditInspectionToolsSettingsAction.invoke(project, editor, file);
  }

  @Override
  public boolean startInWriteAction() {
    return myEditInspectionToolsSettingsAction.startInWriteAction();
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return myEditInspectionToolsSettingsAction.getIcon(flags);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return new IntentionPreviewInfo.Html(InspectionsBundle.message("edit.inspection.options.preview", HighlightDisplayKey.getDisplayNameByKey(myDisplayKey)));
  }
}
