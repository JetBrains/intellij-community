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

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.IDEInspectionToolsConfigurable;
import org.jetbrains.annotations.NotNull;

public class CodeCleanupAction extends CodeInspectionAction {
  public CodeCleanupAction() {
    super("Code Cleanup", "Code Cleanup");
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    final InspectionProfile profile = myExternalProfile != null ? myExternalProfile : InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final InspectionManager managerEx = InspectionManager.getInstance(project);
    final GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase)managerEx.createNewGlobalContext(false);
    globalContext.codeCleanup(project, scope, profile, getTemplatePresentation().getText(), null);
  }

  @Override
  protected IDEInspectionToolsConfigurable createConfigurable(InspectionProjectProfileManager projectProfileManager,
                                                              InspectionProfileManager profileManager) {
    return new IDEInspectionToolsConfigurable(projectProfileManager, profileManager) {
      @Override
      protected boolean acceptTool(InspectionToolWrapper entry) {
        return super.acceptTool(entry) && entry.isCleanupTool();
      }
    };
  }
}
