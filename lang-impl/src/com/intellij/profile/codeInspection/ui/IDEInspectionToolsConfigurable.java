/*
 * User: anna
 * Date: 17-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;

public class IDEInspectionToolsConfigurable extends InspectionToolsConfigurable {
  
  public IDEInspectionToolsConfigurable(final String selectedTool, InspectionProfileManager profileManager) {
    super(selectedTool, profileManager);
  }

  protected InspectionProfileImpl getCurrentProfile() {
    return (InspectionProfileImpl)((InspectionProfileManager)myProfileManager).getRootProfile();
  }

  protected void setCurrentProfile(InspectionProfileImpl profile) {
    ((InspectionProfileManager)myProfileManager).setRootProfile(profile.getName());
  }

  protected boolean areScopesAvailable() {
    return false;
  }
}