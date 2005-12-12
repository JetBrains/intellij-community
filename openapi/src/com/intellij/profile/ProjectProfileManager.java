/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.profile;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.scope.ProfileScope;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public abstract class ProjectProfileManager implements ProfileManager, JDOMExternalizable {

  @Nullable
  public static ProjectProfileManager getProjectProfileFactory(Project project, String profileType){
    final ProjectProfileManager[] components = project.getComponents(ProjectProfileManager.class);
    for (ProjectProfileManager manager : components) {
      if (manager.getProfileType().compareTo(profileType) == 0){
        return manager;
      }
    }
    return null;
  }

  public abstract void assignProfileToScope(String profile, ProfileScope scope);

  public abstract void deassignProfileFromScope(ProfileScope scope);

  public abstract String getProfile(VirtualFile vFile);

  public abstract String getProfile(ProfileScope scope);

  public abstract boolean isProperProfile(ProfileScope scope);

  public abstract Map<ProfileScope,String> getProfilesUsedInProject();

  public abstract void clearProfileScopeAssignments();

  public abstract boolean useProjectLevelProfileSettings();

  public abstract void useProjectLevelProfileSettings(boolean useProjectLevelSettings);
}
