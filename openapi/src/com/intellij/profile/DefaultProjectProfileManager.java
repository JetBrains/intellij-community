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
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public class DefaultProjectProfileManager extends ProjectProfileManager {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProjectProfileManager");

  @NonNls private static final String PROFILES = "profiles";
  @NonNls private static final String SCOPES = "scopes";
  @NonNls private static final String SCOPE = "scope";
  @NonNls private static final String PROFILE = "profile";
  @NonNls private static final String NAME = "name";

  protected Project myProject;
  private LinkedHashMap<NamedScope, String> myScopeToProfileMap = new LinkedHashMap<NamedScope, String>();
  private String myProfileType;

  public String PROJECT_PROFILE;
  protected ApplicationProfileManager myApplicationProfileManager;
  public boolean USE_PROJECT_LEVEL_SETTINGS = false;
  protected Map<String, Profile> myProfiles = new HashMap<String, Profile>();

  public DefaultProjectProfileManager(final Project project, final String profileType) {
    myProject = project;
    myProfileType = profileType;
    myApplicationProfileManager = ApplicationProfileManager.getProfileManager(profileType);
    LOG.assertTrue(myApplicationProfileManager != null);
  }

  public String assignProfileToScope(String profile, @NotNull NamedScope scope) {
    //String projectProfileName = updateProjectProfiles(profile);
    myScopeToProfileMap.put(scope, profile);
    return profile;
  }

  public void deassignProfileFromScope(NamedScope scope) {
    final String profile = myScopeToProfileMap.remove(scope);
    if (!myScopeToProfileMap.containsValue(profile)) {
      myProfiles.remove(profile);
    }
  }

  public String getProfileName(PsiFile psiFile) {
    if (USE_PROJECT_LEVEL_SETTINGS){
      final NamedScopesHolder scopeManager = DependencyValidationManager.getInstance(myProject);
      for (NamedScope scope : myScopeToProfileMap.keySet()) {
        final PackageSet packageSet = scope.getValue();
        if (packageSet != null && packageSet.contains(psiFile, scopeManager)) {
          return myScopeToProfileMap.get(scope);
        }
      }
      final Profile profile = myProfiles.get(PROJECT_PROFILE);
      if (profile != null) return profile.getName();
    }
    return myApplicationProfileManager.getRootProfile().getName();
  }

  public Profile getProfile(@NotNull String name) {
    return USE_PROJECT_LEVEL_SETTINGS ? myProfiles.get(name) : myApplicationProfileManager.getProfile(name);
  }

  public void updateProfile(Profile profile) {
    if (profile.isLocal()) {
      myApplicationProfileManager.updateProfile(profile);
    } else {
      myProfiles.put(profile.getName(), profile);
    }
  }


  public LinkedHashMap<NamedScope, String> getProfilesUsedInProject() {
    return myScopeToProfileMap;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    final Element profiles = element.getChild(PROFILES);
    if (profiles != null) {
      for (Object p : profiles.getChildren(PROFILE)) {
        Element profileElement = (Element)p;
        final Profile profile = myApplicationProfileManager.createProfile();
        profile.readExternal(profileElement);
        myProfiles.put(profile.getName(), profile);
      }
    }
    final Element scopes = element.getChild(SCOPES);
    if (scopes != null) {
      final List children = scopes.getChildren(SCOPE);
      if (children != null) {
        final DependencyValidationManager holder = DependencyValidationManager.getInstance(myProject);
        for (Object s : children) {
          Element scopeElement = (Element)s;
          final String profile = scopeElement.getAttributeValue(PROFILE);
          if (profile != null) {
            final NamedScope scope = holder.getScope(scopeElement.getAttributeValue(NAME));
            if (scope != null) {
              myScopeToProfileMap.put(scope, profile);
            }
          }
        }
      }
    }
    if (USE_PROJECT_LEVEL_SETTINGS && PROJECT_PROFILE == null){
      getProjectProfileImpl();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    //if (!USE_PROJECT_LEVEL_SETTINGS) return;
    final Map<NamedScope, String> usedProfiles = getProfilesUsedInProject();
    final HashSet<String> profilesSet = new HashSet<String>(usedProfiles.values());
    profilesSet.add(PROJECT_PROFILE);
    final List<String> sortedProfiles = new ArrayList<String>(profilesSet);
    Collections.sort(sortedProfiles);
    if (!sortedProfiles.isEmpty()) {
      final Element assignedScopes = new Element(SCOPES);
      List<NamedScope> scopes = new ArrayList<NamedScope>(usedProfiles.keySet());
      Collections.sort(scopes, new Comparator<NamedScope>() {
        public int compare(final NamedScope o1, final NamedScope o2) {
          LOG.assertTrue(o1 != null);
          LOG.assertTrue(o2 != null);
          return o1.getName().compareTo(o2.getName());
        }
      });
      for (NamedScope scope : scopes) {
        final Element scopeElement = new Element(SCOPE);
        scopeElement.setAttribute(PROFILE, usedProfiles.get(scope));
        scopeElement.setAttribute(NAME, scope.getName());
        assignedScopes.addContent(scopeElement);
      }
      element.addContent(assignedScopes);

      final Element profiles = new Element(PROFILES);
      for (String profile : sortedProfiles) {
        final Profile projectProfile = myProfiles.get(profile);
        if (projectProfile != null) {
          final Element profileElement = new Element(PROFILE);
          projectProfile.writeExternal(profileElement);
          profiles.addContent(profileElement);
        }
      }
      element.addContent(profiles);
    }
  }

  public String getProfileType() {
    return myProfileType;
  }

  public Map<String,Profile> getProfiles() {
    return myProfiles;
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

  public void updateAdditionalSettings() {
  }

  public String[] getAvailableProfileNames() {
    return USE_PROJECT_LEVEL_SETTINGS
           ? myProfiles.keySet().toArray(new String[myProfiles.keySet().size()])
           : myApplicationProfileManager.getAvailableProfileNames();
  }

  public void deleteProfile(String name) {
    myProfiles.remove(name);
    if (USE_PROJECT_LEVEL_SETTINGS) {
      for (Iterator<NamedScope> iterator = myScopeToProfileMap.keySet().iterator(); iterator.hasNext();) {
        NamedScope scope = iterator.next();
        if (Comparing.strEqual(myScopeToProfileMap.get(scope), name)) {
          iterator.remove();
        }
      }
    }
    else {
      myApplicationProfileManager.deleteProfile(name);
    }
  }

  public File createUniqueProfileFile(final String profileName) throws IOException {
    return null;
  }

  public String getProjectProfile() {
    return PROJECT_PROFILE;
  }

  public void setProjectProfile(final String projectProfile) {
    PROJECT_PROFILE = projectProfile;
  }

  public Profile getProjectProfileImpl(){
    if (PROJECT_PROFILE == null || myProfiles.isEmpty()){
      @NonNls final String projectProfileName = "Project Default";
      setProjectProfile(projectProfileName);
      final Profile projectProfile = myApplicationProfileManager.createProfile();
      projectProfile.copyFrom(myApplicationProfileManager.getRootProfile());
      projectProfile.setLocal(false);
      projectProfile.setName(projectProfileName);
      myProfiles.put(projectProfileName, projectProfile);
    } else if (!myProfiles.containsKey(PROJECT_PROFILE)){
      final String projectProfileAttempt = myProfiles.keySet().iterator().next();
      setProjectProfile(projectProfileAttempt);
    }
    return myProfiles.get(PROJECT_PROFILE);
  }
}
