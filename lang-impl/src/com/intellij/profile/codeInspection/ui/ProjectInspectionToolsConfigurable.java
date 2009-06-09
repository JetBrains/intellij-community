/*
 * User: anna
 * Date: 17-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

public class ProjectInspectionToolsConfigurable extends InspectionToolsConfigurable {
  public static ProjectInspectionToolsConfigurable getInstance(Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ProjectInspectionToolsConfigurable.class);
  }

  public ProjectInspectionToolsConfigurable(InspectionProfileManager profileManager, InspectionProjectProfileManager projectProfileManager) {
    super(projectProfileManager, profileManager);

  }

  protected InspectionProfileImpl getCurrentProfile() {
    return (InspectionProfileImpl)((InspectionProjectProfileManager)myProjectProfileManager).getProjectProfileImpl();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    final String activeProfileName = getActiveProfile().getName();
    if (getSelectedPanel().isProfileShared()) {
      myProjectProfileManager.setProjectProfile(activeProfileName);
    } else {
      myProfileManager.setRootProfile(activeProfileName);
      myProjectProfileManager.setProjectProfile(null);
    }
  }

  @Override
  public boolean isModified() {
    if (!Comparing.strEqual(getCurrentProfile().getName(), getActiveProfile().getName())) return true;
    return super.isModified();
  }
}