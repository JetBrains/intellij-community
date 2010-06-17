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
package com.intellij.profile;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public abstract class DefaultProjectProfileManager extends ProjectProfileManager {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProjectProfileManager");

  @NonNls protected static final String PROFILES = "profiles";
  @NonNls public static final String SCOPES = "scopes";
  @NonNls protected static final String SCOPE = "scope";
  @NonNls public static final String PROFILE = "profile";
  @NonNls protected static final String NAME = "name";


  private static final String VERSION = "1.0";

  protected final Project myProject;

  public String PROJECT_PROFILE;
  public boolean USE_PROJECT_PROFILE = !isPlatform();

  private final ApplicationProfileManager myApplicationProfileManager;

  private final Map<String, Profile> myProfiles = new HashMap<String, Profile>();
  private final DependencyValidationManager myHolder;
  private final List<ProfileChangeAdapter> myProfilesListener = new ArrayList<ProfileChangeAdapter>();
  @NonNls private static final String PROJECT_DEFAULT_PROFILE_NAME = "Project Default";

  public DefaultProjectProfileManager(final Project project, final ApplicationProfileManager applicationProfileManager,
                                      final DependencyValidationManager holder) {
    myProject = project;
    myHolder = holder;
    myApplicationProfileManager = applicationProfileManager;
    LOG.assertTrue(myApplicationProfileManager != null);
  }

  public Project getProject() {
    return myProject;
  }

  public Profile getProfile(@NotNull String name, boolean returnRootProfileIfNamedIsAbsent) {
    return myProfiles.containsKey(name) ? myProfiles.get(name) : myApplicationProfileManager.getProfile(name, returnRootProfileIfNamedIsAbsent);
  }

  public void updateProfile(Profile profile) {
    myProfiles.put(profile.getName(), profile);
    for (ProfileChangeAdapter profileChangeAdapter : myProfilesListener) {
      profileChangeAdapter.profileChanged(profile);
    }
  }



  public void readExternal(Element element) throws InvalidDataException {
    myProfiles.clear();
    DefaultJDOMExternalizer.readExternal(this, element);
    final Element profilesElement = element.getChild(PROFILES);
    if (profilesElement != null) {
      for (Object o : profilesElement.getChildren(PROFILE)) {
        final Profile profile = myApplicationProfileManager.createProfile();
        profile.setProfileManager(this);
        profile.readExternal((Element)o);
        final String name = profile.getName();
        if (myApplicationProfileManager.getProfile(name) != null) { //override ide profile
          // myApplicationProfileManager.deleteProfile(name);
        }
        myProfiles.put(name, profile);
      }
    }
    if (element.getChild("version") == null || !Comparing.strEqual(element.getChild("version").getAttributeValue("value"), VERSION)) {
      boolean toConvert = true;
      for (Object o : element.getChildren("option")) {
        if (Comparing.strEqual(((Element)o).getAttributeValue("name"), "USE_PROJECT_LEVEL_SETTINGS")) {
          toConvert = Boolean.parseBoolean(((Element)o).getAttributeValue("value"));
          break;
        }
      }
      if (toConvert) {
        convert(element);
      }
    }
  }

  protected void convert(Element element) throws InvalidDataException {
  }

  public void writeExternal(Element element) throws WriteExternalException {

    final List<String> sortedProfiles = new ArrayList<String>(myProfiles.keySet());
    Element profiles = null;
    Collections.sort(sortedProfiles);
    for (String profile : sortedProfiles) {
      final Profile projectProfile = myProfiles.get(profile);
      if (projectProfile != null) {
        final Element profileElement = new Element(PROFILE);
        projectProfile.writeExternal(profileElement);
        boolean hasSmthToSave = sortedProfiles.size() > 1 || !Comparing.strEqual(PROJECT_PROFILE, PROJECT_DEFAULT_PROFILE_NAME);
        if (!hasSmthToSave) {
          for (Object child : profileElement.getChildren()) {
            if (!((Element)child).getName().equals("option")) {
              hasSmthToSave = true;
              break;
            }
          }
        }
        if (!hasSmthToSave) continue;

        if (profiles == null) {
          profiles = new Element(PROFILES);
          element.addContent(profiles);
        }

        profiles.addContent(profileElement);
      }
    }

    if (profiles != null) {
      DefaultJDOMExternalizer.writeExternal(this, element);
      final Element version = new Element("version");
      version.setAttribute("value", VERSION);
      element.addContent(version);
    }
  }

  public NamedScopesHolder getScopesManager() {
    return myHolder;
  }

  public Collection<Profile> getProfiles() {
    getProjectProfileImpl();
    return myProfiles.values();
  }

  public String[] getAvailableProfileNames() {
    return ArrayUtil.toStringArray(myProfiles.keySet());
  }

  public void deleteProfile(String name) {
    myProfiles.remove(name);
  }

  public String getProjectProfile() {
    return PROJECT_PROFILE;
  }

  public void setProjectProfile(final String projectProfile) {
    final String profileName = PROJECT_PROFILE;
    PROJECT_PROFILE = projectProfile;
    USE_PROJECT_PROFILE = projectProfile != null;
    for (ProfileChangeAdapter adapter : myProfilesListener) {
      adapter.profileActivated(profileName != null ? getProfile(profileName) : null, projectProfile != null ?  getProfile(projectProfile) : null);
    }
  }

  @NotNull
  public Profile getProjectProfileImpl(){
    if (isPlatform() || !USE_PROJECT_PROFILE) return myApplicationProfileManager.getRootProfile();
    if (PROJECT_PROFILE == null || myProfiles.isEmpty()){
      setProjectProfile(PROJECT_DEFAULT_PROFILE_NAME);
      final Profile projectProfile = myApplicationProfileManager.createProfile();
      projectProfile.copyFrom(myApplicationProfileManager.getRootProfile());
      projectProfile.setLocal(false);
      projectProfile.setName(PROJECT_DEFAULT_PROFILE_NAME);
      myProfiles.put(PROJECT_DEFAULT_PROFILE_NAME, projectProfile);
    } else if (!myProfiles.containsKey(PROJECT_PROFILE)){
      final String projectProfileAttempt = myProfiles.keySet().iterator().next();
      setProjectProfile(projectProfileAttempt);
    }
    final Profile profile = myProfiles.get(PROJECT_PROFILE);
    profile.setProfileManager(this);
    return profile;
  }

  private static boolean isPlatform() {
    return !ApplicationNamesInfo.getInstance().getLowercaseProductName().equals("Idea");
  }

  public void addProfilesListener(ProfileChangeAdapter profilesListener) {
    myProfilesListener.add(profilesListener);
  }

  public void removeProfilesListener(ProfileChangeAdapter profilesListener) {
    myProfilesListener.remove(profilesListener);
  }

  public static class ProfileStateSplitter implements StateSplitter {

    public List<Pair<Element, String>> splitState(final Element e) {
      final UniqueNameGenerator generator = new UniqueNameGenerator();
      List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();

      final Element[] elements = JDOMUtil.getElements(e);
      for (Element element : elements) {
        if (element.getName().equals("profiles")) {
          element.detach();

          final Element[] profiles = JDOMUtil.getElements(element);
          for (Element profile : profiles) {
            String profileName = null;

            final Element[] options = JDOMUtil.getElements(profile);
            for (Element option : options) {
              if (option.getName().equals("option") && option.getAttributeValue("name").equals("myName")) {
                profileName = option.getAttributeValue("value");
              }
            }

            assert profileName != null;

            final String name = generator.generateUniqueName(FileUtil.sanitizeFileName(profileName)) + ".xml";
            result.add(new Pair<Element, String>(profile,  name));
          }
        }
      }


      if (!e.getContent().isEmpty()) result.add(new Pair<Element, String>(e, generator.generateUniqueName("profiles_settings") + ".xml"));

      return result;
    }

    public void mergeStatesInto(final Element target, final Element[] elements) {
      Element profiles = new Element("profiles");
      target.addContent(profiles);

        for (Element element : elements) {
        if (element.getName().equals("profile")) {
          element.detach();
          profiles.addContent(element);
        }
        else {
          final Element[] states = JDOMUtil.getElements(element);
          for (Element state : states) {
            state.detach();
            target.addContent(state);
          }
        }
      }
    }
  }

}
