/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 17-Apr-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManagerImpl;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

import java.util.Arrays;

public class ProjectInspectionToolsConfigurable extends InspectionToolsConfigurable {
  private static final Logger LOG = Logger.getInstance("#" + ProjectInspectionToolsConfigurable.class.getName());
  public ProjectInspectionToolsConfigurable(InspectionProfileManager profileManager, InspectionProjectProfileManager projectProfileManager) {
    super(projectProfileManager, profileManager);
  }

  @Override
  protected InspectionProfileImpl getCurrentProfile() {
    return (InspectionProfileImpl)myProjectProfileManager.getProjectProfileImpl();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    final InspectionProfileImpl selectedObject = getSelectedObject();
    LOG.assertTrue(selectedObject != null);
    final String profileName = selectedObject.getName();
    final SingleInspectionProfilePanel selectedPanel = getSelectedPanel();
    LOG.assertTrue(selectedPanel != null, "selected profile: " + profileName + " panels: " + Arrays.toString(getKnownNames().toArray()));
    if (selectedPanel.isProfileShared()) {
      myProjectProfileManager.setProjectProfile(profileName);
    } else {
      myProfileManager.setRootProfile(profileName);
      myProjectProfileManager.setProjectProfile(null);
    }
    InspectionProfileManagerImpl.onProfilesChanged();
  }

  @Override
  public boolean isModified() {
    final InspectionProfileImpl selectedObject = getSelectedObject();
    if (selectedObject != null && !Comparing.strEqual(getCurrentProfile().getName(), selectedObject.getName())) return true;
    return super.isModified();
  }
}