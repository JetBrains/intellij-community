/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInspection.ex.InspectionProfileManagerImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;

public class ProjectInspectionToolsConfigurable extends InspectionToolsConfigurable {
  public ProjectInspectionToolsConfigurable(InspectionProfileManager profileManager, InspectionProjectProfileManager projectProfileManager) {
    super(projectProfileManager, profileManager);
  }

  @Override
  protected InspectionProfileImpl getCurrentProfile() {
    return (InspectionProfileImpl)myProjectProfileManager.getProjectProfileImpl();
  }

  @Override
  protected void applyRootProfile(String name, boolean isShared) {
    if (isShared) {
      myProjectProfileManager.setProjectProfile(name);
    }
    else {
      myProfileManager.setRootProfile(name);
      myProjectProfileManager.setProjectProfile(null);
    }
    InspectionProfileManagerImpl.onProfilesChanged();
  }
}