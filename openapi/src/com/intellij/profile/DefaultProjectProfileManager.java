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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
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
public class DefaultProjectProfileManager extends ProjectProfileManager {
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
  @NonNls private static final String CURRENT_PROFILE = "current_profile";


  protected Project myProject;
  private Map<ProfileScope, String> myScopeToProfileMap = new HashMap<ProfileScope, String>();
  private String myProfileType;

  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProjectProfileManager");
  @NonNls private static final String PROFILES = "profiles";

  public boolean USE_PROJECT_LEVEL_SETTINGS = false;

  public DefaultProjectProfileManager(final Project project,
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
    return getProfile(ProfileScopeFactory.getInstance(myProject).findProfileScopeForFile(vFile));
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
    final ProfileManager profileManager = ProfileManager.getProfileManager(myProfileType);
    LOG.assertTrue(profileManager != null);
    return profileManager.getRootProfile().getName();
  }

  public boolean isProperProfile(ProfileScope scope) {
    return scope instanceof ApplicationProfileScope || myScopeToProfileMap.containsKey(scope);
  }

  public Map<ProfileScope, String> getProfilesUsedInProject() {
    return myScopeToProfileMap;
  }

  public void readExternal(Element element) throws InvalidDataException {
    final ProfileManager profileManager = ProfileManager.getProfileManager(myProfileType);
    LOG.assertTrue(profileManager != null);
    final Element profiles = element.getChild(PROFILES);
    if (profiles != null) {
      profileManager.readProfiles(profiles);
    }
    final Element scopes = element.getChild(SCOPES);
    if (scopes != null) {
      final List children = scopes.getChildren(SCOPE);
      if (children != null) {
        for (Object s : children) {
          Element scopeElement = (Element)s;
          final String profile = scopeElement.getAttributeValue(PROFILE);
          if (profile != null) {
            assignProfileToScope(profile, getProfileScope(scopeElement));
          }
        }
      }
    }
    final Element currentProfile = element.getChild(CURRENT_PROFILE);
    if (currentProfile != null){
      final String projectProfile = currentProfile.getAttributeValue(PROFILE);
      if (!Comparing.strEqual(profileManager.getRootProfile().getName(), projectProfile)){
         final Profile profile = profileManager.createProfile();
         profile.readExternal(currentProfile);
         profileManager.addProfile(profile);
         assignProfileToScope(projectProfile, ProfileScopeFactory.getInstance(myProject).getProfileScope());
      }
    }
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final ProfileManager profileManager = ProfileManager.getProfileManager(myProfileType);
    LOG.assertTrue(profileManager != null);
    final Map<ProfileScope, String> usedProfiles = getProfilesUsedInProject();
    final List<String> differentUsedProfiles = new ArrayList<String>(new HashSet<String>(usedProfiles.values()));
    Collections.sort(differentUsedProfiles);
    if (!usedProfiles.isEmpty()) {
      final Element assignedScopes = new Element(SCOPES);
      List<ProfileScope> scopes = new ArrayList<ProfileScope>(usedProfiles.keySet());
      Collections.sort(scopes, new Comparator<ProfileScope>() {
        public int compare(final ProfileScope o1, final ProfileScope o2) {
          LOG.assertTrue(o1 != null);
          LOG.assertTrue(o2 != null);
          return o1.getName().compareTo(o2.getName());
        }
      });
      for (ProfileScope scope : scopes) {
        final Element scopeElement = new Element(SCOPE);
        scope.write(scopeElement);
        scopeElement.setAttribute(PROFILE, usedProfiles.get(scope));
        assignedScopes.addContent(scopeElement);
      }
      element.addContent(assignedScopes);

      final Element profiles = new Element(PROFILES);
      for (String profile : differentUsedProfiles) {
        final Profile projectProfile = profileManager.getProfile(profile);
        if (projectProfile != null) {
          final Element profileElement = new Element(PROFILE);
          projectProfile.writeExternal(profileElement);
          profiles.addContent(profileElement);
        }
      }
      element.addContent(profiles);
    }
    final ProfileScope profileScope = ProfileScopeFactory.getInstance(myProject).getProfileScope();
    if (USE_PROJECT_LEVEL_SETTINGS && !isProperProfile(profileScope)){
      final Element currentProjectProfile = new Element(CURRENT_PROFILE);
      final Profile rootProfile = profileManager.getRootProfile();
      final String name = rootProfile.getName();
      currentProjectProfile.setAttribute(PROFILE, name);
      if (!differentUsedProfiles.contains(name)){
        rootProfile.writeExternal(currentProjectProfile);
      }
      element.addContent(currentProjectProfile);
    }
    DefaultJDOMExternalizer.writeExternal(this, element);
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

  public boolean useProjectLevelProfileSettings() {
    return USE_PROJECT_LEVEL_SETTINGS;
  }

  public void useProjectLevelProfileSettings(boolean useProjectLevelSettings) {
    USE_PROJECT_LEVEL_SETTINGS = useProjectLevelSettings;
  }

  public boolean isModified(ProjectProfileManager manager) {
    final Map<ProfileScope, String> usedProfiles = manager.getProfilesUsedInProject();
    final Map<ProfileScope, String> currentUsedProfiles = getProfilesUsedInProject();
    if (usedProfiles.size() != currentUsedProfiles.size()) return true;
    for (ProfileScope scope : usedProfiles.keySet()) {
      if (!Comparing.strEqual(currentUsedProfiles.get(scope), usedProfiles.get(scope))) return true;
    }
    return USE_PROJECT_LEVEL_SETTINGS != manager.useProjectLevelProfileSettings();
  }

  public void copy(ProjectProfileManager manager) {
    clearProfileScopeAssignments();
    final Map<ProfileScope, String> profilesUsedInProject = manager.getProfilesUsedInProject();
    for (ProfileScope scope : profilesUsedInProject.keySet()) {
      assignProfileToScope(profilesUsedInProject.get(scope), scope);
    }
    useProjectLevelProfileSettings(manager.useProjectLevelProfileSettings());
  }

  public ProjectProfileManager getModifiableModel() {
    return new DefaultProjectProfileManager(myProject, getProfileType());
  }

  public void updateAdditionalSettings() {
  }
}
