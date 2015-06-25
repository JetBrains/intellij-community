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
package com.intellij.profile;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.MainConfigurationStateSplitter;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public abstract class DefaultProjectProfileManager extends ProjectProfileManager implements PersistentStateComponent<Element> {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProjectProfileManager");

  @NonNls public static final String SCOPES = "scopes";
  @NonNls protected static final String SCOPE = "scope";
  @NonNls public static final String PROFILE = "profile";
  @NonNls protected static final String NAME = "name";

  private static final String VERSION = "1.0";

  @NotNull
  protected final Project myProject;

  protected String myProjectProfile;
  /** This field is used for serialization. Do not rename it or make access weaker */
  public boolean USE_PROJECT_PROFILE = true;

  private final ApplicationProfileManager myApplicationProfileManager;

  private final Map<String, Profile> myProfiles = new THashMap<String, Profile>();
  protected final DependencyValidationManager myHolder;
  private final List<ProfileChangeAdapter> myProfilesListener = ContainerUtil.createLockFreeCopyOnWriteList();
  @NonNls private static final String PROJECT_DEFAULT_PROFILE_NAME = "Project Default";

  public DefaultProjectProfileManager(@NotNull final Project project,
                                      @NotNull ApplicationProfileManager applicationProfileManager,
                                      @NotNull DependencyValidationManager holder) {
    myProject = project;
    myHolder = holder;
    myApplicationProfileManager = applicationProfileManager;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public synchronized Profile getProfile(@NotNull String name, boolean returnRootProfileIfNamedIsAbsent) {
    return myProfiles.containsKey(name) ? myProfiles.get(name) : myApplicationProfileManager.getProfile(name, returnRootProfileIfNamedIsAbsent);
  }

  @Override
  public synchronized void updateProfile(@NotNull Profile profile) {
    myProfiles.put(profile.getName(), profile);
    for (ProfileChangeAdapter profileChangeAdapter : myProfilesListener) {
      profileChangeAdapter.profileChanged(profile);
    }
  }

  @Nullable
  @Override
  public synchronized Element getState() {
    Element state = new Element("settings");

    String[] sortedProfiles = myProfiles.keySet().toArray(new String[myProfiles.size()]);
    Arrays.sort(sortedProfiles);
    for (String profile : sortedProfiles) {
      final Profile projectProfile = myProfiles.get(profile);
      if (projectProfile != null) {
        Element profileElement = new Element(PROFILE);
        try {
          projectProfile.writeExternal(profileElement);
        }
        catch (WriteExternalException e) {
          LOG.error(e);
        }
        boolean hasSmthToSave = sortedProfiles.length > 1 || isCustomProfileUsed();
        if (!hasSmthToSave) {
          for (Element child : profileElement.getChildren()) {
            if (!child.getName().equals("option")) {
              hasSmthToSave = true;
              break;
            }
          }
        }
        if (hasSmthToSave) {
          state.addContent(profileElement);
        }
      }
    }

    if (!state.getChildren().isEmpty() || isCustomProfileUsed()) {
      XmlSerializer.serializeInto(this, state);
      state.addContent(new Element("version").setAttribute("value", VERSION));
    }
    return state;
  }

  @Override
  public synchronized void loadState(Element state) {
    myProfiles.clear();
    XmlSerializer.deserializeInto(this, state);
    for (Element o : state.getChildren(PROFILE)) {
      Profile profile = myApplicationProfileManager.createProfile();
      profile.setProfileManager(this);
      try {
        profile.readExternal(o);
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
      profile.setProjectLevel(true);
      myProfiles.put(profile.getName(), profile);
    }
    if (state.getChild("version") == null || !Comparing.strEqual(state.getChild("version").getAttributeValue("value"), VERSION)) {
      boolean toConvert = true;
      for (Element o : state.getChildren("option")) {
        if (Comparing.strEqual(o.getAttributeValue("name"), "USE_PROJECT_LEVEL_SETTINGS")) {
          toConvert = Boolean.parseBoolean(o.getAttributeValue("value"));
          break;
        }
      }
      if (toConvert) {
        convert(state);
      }
    }
  }

  protected void convert(Element element) {
  }

  private boolean isCustomProfileUsed() {
    return myProjectProfile != null && !Comparing.strEqual(myProjectProfile, PROJECT_DEFAULT_PROFILE_NAME);
  }

  @NotNull
  @Override
  public NamedScopesHolder getScopesManager() {
    return myHolder;
  }

  @NotNull
  @Override
  public synchronized Collection<Profile> getProfiles() {
    getProjectProfileImpl();
    return myProfiles.values();
  }

  @NotNull
  @Override
  public synchronized String[] getAvailableProfileNames() {
    return ArrayUtil.toStringArray(myProfiles.keySet());
  }

  @Override
  public synchronized void deleteProfile(@NotNull String name) {
    myProfiles.remove(name);
  }

  @Override
  @OptionTag("PROJECT_PROFILE")
  public synchronized String getProjectProfile() {
    return myProjectProfile;
  }

  @Override
  public synchronized void setProjectProfile(@Nullable String newProfile) {
    if (Comparing.strEqual(newProfile, myProjectProfile)) {
      return;
    }

    String oldProfile = myProjectProfile;
    myProjectProfile = newProfile;
    USE_PROJECT_PROFILE = newProfile != null;
    if (oldProfile != null) {
      for (ProfileChangeAdapter adapter : myProfilesListener) {
        adapter.profileActivated(getProfile(oldProfile), newProfile != null ?  getProfile(newProfile) : null);
      }
    }
  }

  @NotNull
  public synchronized Profile getProjectProfileImpl(){
    if (!USE_PROJECT_PROFILE) {
      return myApplicationProfileManager.getRootProfile();
    }
    if (myProjectProfile == null || myProfiles.isEmpty()){
      setProjectProfile(PROJECT_DEFAULT_PROFILE_NAME);
      final Profile projectProfile = myApplicationProfileManager.createProfile();
      projectProfile.copyFrom(myApplicationProfileManager.getRootProfile());
      projectProfile.setProjectLevel(true);
      projectProfile.setName(PROJECT_DEFAULT_PROFILE_NAME);
      myProfiles.put(PROJECT_DEFAULT_PROFILE_NAME, projectProfile);
    }
    else if (!myProfiles.containsKey(myProjectProfile)){
      setProjectProfile(myProfiles.keySet().iterator().next());
    }
    final Profile profile = myProfiles.get(myProjectProfile);
    profile.setProfileManager(this);
    return profile;
  }

  public void addProfilesListener(@NotNull final ProfileChangeAdapter profilesListener, @NotNull Disposable parent) {
    myProfilesListener.add(profilesListener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myProfilesListener.remove(profilesListener);
      }
    });
  }

  public void removeProfilesListener(@NotNull ProfileChangeAdapter profilesListener) {
    myProfilesListener.remove(profilesListener);
  }

  public static class ProfileStateSplitter extends MainConfigurationStateSplitter {
    @NotNull
    @Override
    protected String getComponentStateFileName() {
      return "profiles_settings";
    }

    @NotNull
    @Override
    protected String getSubStateTagName() {
      return PROFILE;
    }
  }

  protected void fireProfilesInitialized() {
    for (ProfileChangeAdapter profileChangeAdapter : myProfilesListener) {
      profileChangeAdapter.profilesInitialized();
    }
  }
  protected void fireProfilesShutdown() {
    for (ProfileChangeAdapter profileChangeAdapter : myProfilesListener) {
      profileChangeAdapter.profilesShutdown();
    }
  }
}
