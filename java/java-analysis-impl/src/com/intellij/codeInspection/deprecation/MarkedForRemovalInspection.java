// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MarkedForRemovalInspection extends DeprecationInspectionBase {

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (file instanceof PsiJavaFile && ((PsiJavaFile)file).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9)) {
      HighlightSeverity severity = getCurrentSeverity(file);
      return ApiUsageUastVisitor.createPsiElementVisitor(
        new DeprecatedApiUsageProcessor(holder, false, false, false, false, IGNORE_IN_SAME_OUTERMOST_CLASS, true, severity)
      );
    }
    return PsiElementVisitor.EMPTY_VISITOR;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getShortName() {
    return DeprecationUtil.FOR_REMOVAL_SHORT_NAME;
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  @NonNls
  public String getID() {
    return DeprecationUtil.FOR_REMOVAL_ID;
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    addSameOutermostClassCheckBox(panel);
    return panel;
  }

  private static HighlightSeverity getCurrentSeverity(@NotNull PsiFile file) {
    HighlightDisplayKey highlightDisplayKey = HighlightDisplayKey.find(DeprecationUtil.FOR_REMOVAL_SHORT_NAME);
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getCurrentProfile();
    HighlightDisplayLevel displayLevel = profile.getErrorLevel(highlightDisplayKey, file);
    return displayLevel.getSeverity();
  }
}
