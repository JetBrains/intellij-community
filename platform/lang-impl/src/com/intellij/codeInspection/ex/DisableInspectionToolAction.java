// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DisableInspectionToolAction extends IntentionAndQuickFixAction implements Iconable, DumbAware {
  private final String myToolId;

  public DisableInspectionToolAction(LocalInspectionTool tool) {
    myToolId = tool.getShortName();
  }

  public DisableInspectionToolAction(final HighlightDisplayKey key) {
    myToolId = key.getShortName();
  }

  @Override
  public @NotNull String getName() {
    return getNameText();
  }

  @Override
  public @NotNull String getFamilyName() {
    return getNameText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfile inspectionProfile = profileManager.getCurrentProfile();
    InspectionToolWrapper toolWrapper = inspectionProfile.getInspectionTool(myToolId, project);
    return toolWrapper == null || !toolWrapper.getDefaultLevel().isNonSwitchable();
  }

  @Override
  public void applyFix(@NotNull Project project, final PsiFile file, @Nullable Editor editor) {
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, it -> it.disableTool(myToolId, file));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }

  public static @IntentionName String getNameText() {
    return InspectionsBundle.message("disable.inspection.action.name");
  }
}
