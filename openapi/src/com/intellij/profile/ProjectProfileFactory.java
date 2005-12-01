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
public abstract class ProjectProfileFactory implements JDOMExternalizable {

  @Nullable
  public static ProjectProfileFactory getProjectProfileFactory(Project project, String profileType){
    final ProjectProfileFactory[] components = project.getComponents(ProjectProfileFactory.class);
    for (ProjectProfileFactory factory : components) {
      if (factory.getProfileType().compareTo(profileType) == 0){
        return factory;
      }
    }
    return null;
  }

  public abstract void assignProfileToScope(String profile, ProfileScope scope);

  public abstract void deassignProfileFromScope(ProfileScope scope);

  public abstract String getProfile(VirtualFile vFile);

  public abstract String getProfile(ProfileScope scope);

  public abstract boolean isProperProfile(ProfileScope scope);

  public abstract Map<ProfileScope,String> getUsedProfiles();

  public abstract String getProfileType();

  public abstract void clearProfileScopeAssignments();
}
