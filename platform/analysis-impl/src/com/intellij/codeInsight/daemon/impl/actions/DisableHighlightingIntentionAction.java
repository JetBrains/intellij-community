// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DisableHighlightingIntentionAction extends IntentionAndQuickFixAction implements DumbAware {
  private final String myShortName;

  public DisableHighlightingIntentionAction(String shortName) { 
    myShortName = shortName; 
  }

  @Override
  public @NotNull String getFamilyName() {
    return AnalysisBundle.message("intention.family.name.disable.highlighting.keep.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    HighlightSeverity usedSeverity = profile.getErrorLevel(HighlightDisplayKey.find(myShortName), file).getSeverity();
    return usedSeverity.compareTo(HighlightSeverity.INFORMATION) > 0;
  }

  @Override
  public @NotNull String getName() {
    return getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, it -> {
      ToolsImpl tools = it.getToolsOrNull(myShortName, project);
      if (tools != null) {
        tools.setLevel(HighlightDisplayLevel.DO_NOT_SHOW);
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
