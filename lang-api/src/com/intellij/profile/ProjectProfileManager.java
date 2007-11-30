/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public abstract class ProjectProfileManager implements ProfileManager, JDOMExternalizable {

  @Nullable
  public static ProjectProfileManager getProjectProfileManager(Project project, String profileType){
    final ProjectProfileManager[] components = project.getComponents(ProjectProfileManager.class);
    for (ProjectProfileManager manager : components) {
      if (manager.getProfileType().compareTo(profileType) == 0){
        return manager;
      }
    }
    return null;
  }

  public abstract String assignProfileToScope(String profile, NamedScope scope);

  public abstract void deassignProfileFromScope(NamedScope scope);

  public abstract String getProfileName(PsiFile psiFile);

  public abstract LinkedHashMap<NamedScope,String> getProfilesUsedInProject();

  public abstract void clearProfileScopeAssignments();

  public abstract boolean useProjectLevelProfileSettings();

  public abstract void useProjectLevelProfileSettings(boolean useProjectLevelSettings);

  public abstract String getProjectProfile();
  public abstract void setProjectProfile(final String projectProfile);
}
