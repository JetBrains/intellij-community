/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui;

import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

/**
 * @author nik
 */
public class ProjectInspectionToolsConfigurableProvider extends ErrorsConfigurableProvider {
  private InspectionProfileManager myProfileManager;
  private InspectionProjectProfileManager myProjectProfileManager;

  public ProjectInspectionToolsConfigurableProvider(InspectionProfileManager profileManager,
                                                    InspectionProjectProfileManager projectProfileManager) {
    myProfileManager = profileManager;
    myProjectProfileManager = projectProfileManager;
  }

  @Override
  public ErrorsConfigurable createConfigurable() {
    return new ProjectInspectionToolsConfigurable(myProfileManager, myProjectProfileManager);
  }
}
