// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.ProjectInspectionToolsConfigurable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EditCleanupProfileIntentionAction implements IntentionAction {
  public static final EditCleanupProfileIntentionAction INSTANCE = new EditCleanupProfileIntentionAction();
  private EditCleanupProfileIntentionAction() {}

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("intention.family.edit.cleanup.profile.settings");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(project);
    ProjectInspectionToolsConfigurable configurable =
      new ProjectInspectionToolsConfigurable(profileManager) {
        @Override
        protected boolean acceptTool(InspectionToolWrapper entry) {
          return super.acceptTool(entry) && entry.isCleanupTool();
        }

        @Override
        public String getDisplayName() {
          return InspectionsBundle.message("configurable.name.code.cleanup.inspections");
        }
      };
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
