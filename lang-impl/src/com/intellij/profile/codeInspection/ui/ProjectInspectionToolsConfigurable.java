/*
 * User: anna
 * Date: 17-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

import java.util.Collection;

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

  protected void setCurrentProfile(InspectionProfileImpl profile) {
    myProjectProfileManager.setProjectProfile(profile.getName());
  }

  protected void deleteProfile(String name) {
    if (myProjectProfileManager.getProfile(name, false) != null) {
      myProjectProfileManager.deleteProfile(name);
    }
  }

  protected Collection<Profile> getProfiles() {
    return myProjectProfileManager.getProfiles();
  }
}