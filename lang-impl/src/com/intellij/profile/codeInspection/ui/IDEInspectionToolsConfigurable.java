/*
 * User: anna
 * Date: 17-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

import javax.swing.*;

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

  @Override
  public boolean isModified() {
    if (!Comparing.strEqual(((InspectionProfileImpl)myProfiles.getSelectedItem()).getName(), getCurrentProfile().getName())) return true;    
    return super.isModified();
  }

  @Override
  public JComponent createComponent() {
    myActivateButton.setVisible(false);
    return super.createComponent();
  }
}