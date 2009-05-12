/*
 * User: anna
 * Date: 17-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

public class IDEInspectionToolsConfigurable extends InspectionToolsConfigurable {
  public IDEInspectionToolsConfigurable(InspectionProjectProfileManager projectProfileManager, InspectionProfileManager profileManager) {
    super(projectProfileManager, profileManager);
  }

  protected InspectionProfileImpl getCurrentProfile() {
    return (InspectionProfileImpl)((InspectionProfileManager)myProfileManager).getRootProfile();
  }

  protected void setCurrentProfile(InspectionProfileImpl profile) {
    myProfileManager.setRootProfile(profile.getName());
  }
}