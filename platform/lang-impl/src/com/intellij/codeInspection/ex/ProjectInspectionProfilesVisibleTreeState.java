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

package com.intellij.codeInspection.ex;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

import java.util.Collection;

@State(
  name = "ProjectInspectionProfilesVisibleTreeState",
  storages = {
    @Storage(
        id="other",
        file = "$WORKSPACE_FILE$"
    )}
)
public class ProjectInspectionProfilesVisibleTreeState implements PersistentStateComponent<VisibleTreeStateComponent> {
  private final InspectionProjectProfileManager myManager;

  public ProjectInspectionProfilesVisibleTreeState(InspectionProjectProfileManager manager) {
    myManager = manager;
  }

  public VisibleTreeStateComponent getState() {
    return new VisibleTreeStateComponent(getProfiles());
  }

  protected Collection<Profile> getProfiles() {
    return myManager.getProfiles();
  }

  public void loadState(final VisibleTreeStateComponent state) {
    state.loadState(getProfiles());
  }
}
