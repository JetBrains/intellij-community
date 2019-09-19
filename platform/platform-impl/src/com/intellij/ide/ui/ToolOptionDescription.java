/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;

/**
 * @author Konstantin Bulenkov
 */
public class ToolOptionDescription extends BooleanOptionDescription {
  private final Project myProject;
  private final String myShortName;

  public ToolOptionDescription(Tools tool, Project project) {
    super(tool.getTool().getGroupDisplayName() + ": " + tool.getTool().getDisplayName(), "Errors");
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
    InspectionProfileImpl.setToolEnabled(enabled, getCurrentProfile(), myShortName, myProject);
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }
}
