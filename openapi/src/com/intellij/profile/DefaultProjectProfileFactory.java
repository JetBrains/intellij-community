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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.scope.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public class DefaultProjectProfileFactory extends ProjectProfileFactory {
  private static Set<ProfileScope> ourProfileScopeTypes = new HashSet<ProfileScope>();

  static {
    ourProfileScopeTypes.add(ApplicationProfileScope.INSTANCE);
    ourProfileScopeTypes.add(new ProjectProfileScope(null));
    ourProfileScopeTypes.add(new ModuleProfileScope(null));
    ourProfileScopeTypes.add(new DirectoryProfileScope(null));
  }


  @NonNls private static final String SCOPES = "scopes";
  @NonNls private static final String SCOPE = "scope";
  @NonNls private static final String PROFILE = "profile";


  private Project myProject;
  private Map<ProfileScope, String> myScopeToProfileMap = new HashMap<ProfileScope, String>();
  private String myProfileType;

  public DefaultProjectProfileFactory(final Project project,
                                      final String profileType) {
    myProject = project;
    myProfileType = profileType;
  }

  public void assignProfileToScope(String profile, @NotNull ProfileScope scope) {
    myScopeToProfileMap.put(scope, profile);
  }

  public void deassignProfileFromScope(ProfileScope scope) {
    myScopeToProfileMap.remove(scope);
  }

  public String getProfile(VirtualFile vFile) {
    return getProfile(ProfileScopeFactory.getInstance().findProfileScopeForFile(vFile, myProject));
  }

  public String getProfile(final ProfileScope scope) {
    final String profile = myScopeToProfileMap.get(scope);
    if (profile != null) {
      return profile;
    }
    ProfileScope parentScope = scope.getParentScope(myProject);
    while (parentScope != null) {
      if (myScopeToProfileMap.containsKey(parentScope)) {
        return myScopeToProfileMap.get(parentScope);
      }
      parentScope = parentScope.getParentScope(myProject);
    }
    final ProfileFactory profileFactory = ProfileFactory.getProfileFactory(myProfileType);
    assert profileFactory != null;
    return profileFactory.getRootProfile().getName();
  }

  public boolean isProperProfile(ProfileScope scope) {
    return scope instanceof ApplicationProfileScope || myScopeToProfileMap.containsKey(scope);
  }

  public Map<ProfileScope, String> getUsedProfiles() {
    return myScopeToProfileMap;
  }

  public void readExternal(Element element) throws InvalidDataException {
    final ProfileFactory profileFactory = ProfileFactory.getProfileFactory(myProfileType);
    assert profileFactory != null;
    profileFactory.readProfiles(element);
    final List scopes = element.getChildren(SCOPES);
    if (scopes != null) {
      for (Object s : scopes) {
        Element scopeElement = (Element)s;
        final String profile = scopeElement.getAttributeValue(PROFILE);
        if (profile != null) {
          assignProfileToScope(profile, getProfileScope(scopeElement));
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final ProfileFactory profileFactory = ProfileFactory.getProfileFactory(myProfileType);
    assert profileFactory != null;
    profileFactory.writeProfiles(element, false);
    final Map<ProfileScope, String> usedProfiles = getUsedProfiles();
    if (!usedProfiles.isEmpty()) {
      final Element assignedScopes = new Element(SCOPES);
      for (ProfileScope scope : usedProfiles.keySet()) {
        final Element scopeElement = new Element(SCOPE);
        scope.write(scopeElement);
        scopeElement.setAttribute(PROFILE, usedProfiles.get(scope));
        assignedScopes.addContent(scopeElement);
      }
      element.addContent(assignedScopes);
    }
  }

  private ProfileScope getProfileScope(Element element) {
    for (ProfileScope scope : ourProfileScopeTypes) {
      final ProfileScope profileScope = scope.createProfileScope(myProject, element);
      if (profileScope != null) {
        return profileScope;
      }
    }
    return null;
  }

  public String getProfileType() {
    return myProfileType;
  }

  public void clearProfileScopeAssignments() {
    myScopeToProfileMap.clear();
  }

  public ProjectProfileFactory copy() {
    final DefaultProjectProfileFactory copy = new DefaultProjectProfileFactory(myProject, myProfileType);
    for (ProfileScope scope : myScopeToProfileMap.keySet()) {
      copy.assignProfileToScope(myScopeToProfileMap.get(scope), scope);
    }
    return copy;
  }
}
