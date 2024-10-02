// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.application.options.schemes.SchemesCombo;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CodeCleanupAction extends CodeInspectionAction {

  public CodeCleanupAction() {
    super(InspectionsBundle.message("dialog.title.code.cleanup"), InspectionsBundle.message("dialog.noun.code.cleanup"));
  }

  @Override
  protected void runInspections(@NotNull Project project,
                                @NotNull AnalysisScope scope) {
    final InspectionProfile profile = myExternalProfile != null ? myExternalProfile : InspectionProjectProfileManager.getInstance(project)
      .getCurrentProfile();
    final InspectionManager managerEx = InspectionManager.getInstance(project);
    final GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase)managerEx.createNewGlobalContext();
    globalContext.codeCleanup(scope, profile, getTemplatePresentation().getText(), null, false);
  }

  @Override
  protected String getHelpTopic() {
    return "reference.dialogs.cleanup.scope";
  }

  @Override
  protected ExternalProfilesComboboxAwareInspectionToolsConfigurable createConfigurable(ProjectInspectionProfileManager projectProfileManager,
                                                                                        SchemesCombo<InspectionProfileImpl> profilesCombo) {
    return new ExternalProfilesComboboxAwareInspectionToolsConfigurable(projectProfileManager, profilesCombo) {
      @Override
      protected boolean acceptTool(InspectionToolWrapper entry) {
        return super.acceptTool(entry) && entry.isCleanupTool();
      }

      @Override
      public String getDisplayName() {
        return InspectionsBundle.message("configurable.name.code.cleanup.inspections");
      }
    };
  }
}
