/*
 * User: anna
 * Date: 17-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

public class ProjectInspectionToolsConfigurable extends InspectionToolsConfigurable {
  public static ProjectInspectionToolsConfigurable getInstance(Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ProjectInspectionToolsConfigurable.class);
  }

  public ProjectInspectionToolsConfigurable(ProfileManager profileManager) {
    super(profileManager);
  }

  protected InspectionProfileImpl getCurrentProfile() {
    return (InspectionProfileImpl)((InspectionProjectProfileManager)myProfileManager).getProjectProfileImpl();
  }

  protected void setCurrentProfile(InspectionProfileImpl profile) {
    ((InspectionProjectProfileManager)myProfileManager).setProjectProfile(profile.getName());
  }

  protected boolean areScopesAvailable() {
    return true;
  }
}