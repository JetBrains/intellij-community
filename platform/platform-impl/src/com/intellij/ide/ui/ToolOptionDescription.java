// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ToolOptionDescription extends BooleanOptionDescription {
  private final Project myProject;
  private final String myShortName;

  public ToolOptionDescription(InspectionToolWrapper<?, ?> tool, Project project) {
    super(tool.getGroupDisplayName() + ": " + tool.getDisplayName(), "Errors");
    myShortName = tool.getShortName();
    myProject = project;
  }

  @Override
  public boolean isOptionEnabled() {
    return getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(myShortName));
  }

  private InspectionProfileImpl getCurrentProfile() {
    return InspectionProfileManager.getInstance(myProject).getCurrentProfile();
  }

  @Override
  public void setOptionState(boolean enabled) {
    InspectionProfileImpl.setToolEnabled(enabled, getCurrentProfile(), myShortName, true, myProject);
    DaemonCodeAnalyzerEx.getInstanceEx(myProject).restart("ToolOptionDescription.setOptionState");
  }
}
