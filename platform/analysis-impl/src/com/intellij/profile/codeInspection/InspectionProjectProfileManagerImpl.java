/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.ProfileEx;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
@State(
  name = "InspectionProjectProfileManager",
  storages = @Storage(value = "inspectionProfiles", stateSplitter = InspectionProjectProfileManagerImpl.ProfileStateSplitter.class)
)
public class InspectionProjectProfileManagerImpl implements PersistentStateComponent<Element>, InspectionProjectProfileManager,
                                                            ProjectComponent {
  private static final Logger LOG = Logger.getInstance(InspectionProjectProfileManagerImpl.class);
  private static final String VERSION = "1.0";

  private final Map<String, InspectionProfileWrapper> myName2Profile = new ConcurrentHashMap<String, InspectionProfileWrapper>();
  private final Map<String, InspectionProfileWrapper> myAppName2Profile = new ConcurrentHashMap<String, InspectionProfileWrapper>();

  private final SeverityRegistrar mySeverityRegistrar;
  private final NamedScopeManager myLocalScopesHolder;
  private NamedScopesHolder.ScopeListener myScopeListener;

  private final Map<String, Profile> myProfiles = new THashMap<>();
  protected final DependencyValidationManager myHolder;
  private final List<ProfileChangeAdapter> myProfilesListener = ContainerUtil.createLockFreeCopyOnWriteList();

  private final ApplicationProfileManager myApplicationProfileManager;

  @NonNls public static final String SCOPES = "scopes";
  @NonNls protected static final String SCOPE = "scope";
  @NonNls public static final String PROFILE = "profile";
  @NonNls protected static final String NAME = "name";

  @NonNls private static final String PROJECT_DEFAULT_PROFILE_NAME = "Project Default";

  @NotNull
  protected final Project myProject;

  private String myProjectProfile;

  @OptionTag("USE_PROJECT_PROFILE")
  private boolean useProjectProfile = true;


  @NotNull
  public Project getProject() {
    return myProject;
  }

  public InspectionProjectProfileManagerImpl(@NotNull Project project,
                                             @NotNull InspectionProfileManager inspectionProfileManager,
                                             @NotNull DependencyValidationManager holder,
                                             @NotNull NamedScopeManager localScopesHolder) {
    myProject = project;
    myApplicationProfileManager = inspectionProfileManager;
    myHolder = holder;

    myLocalScopesHolder = localScopesHolder;
    mySeverityRegistrar = new SeverityRegistrar(project.getMessageBus());
  }

  public static InspectionProjectProfileManagerImpl getInstanceImpl(Project project) {
    return (InspectionProjectProfileManagerImpl)project.getComponent(InspectionProjectProfileManager.class);
  }

  @Override
  public boolean isProfileLoaded() {
    final InspectionProfile profile = getInspectionProfile();
    final String name = profile.getName();
    return profile.getProfileManager() == this ? myName2Profile.containsKey(name) : myAppName2Profile.containsKey(name);
  }

  @NotNull
  public InspectionProfileWrapper getProfileWrapper() {
    InspectionProfile profile = getInspectionProfile();
    String profileName = profile.getName();
    Map<String, InspectionProfileWrapper> nameToProfile = profile.getProfileManager() == this ? myName2Profile : myAppName2Profile;
    InspectionProfileWrapper wrapper = nameToProfile.get(profileName);
    if (wrapper == null) {
      initProfileWrapper(profile);
      return nameToProfile.get(profileName);
    }
    return wrapper;
  }

  @Override
  public synchronized void updateProfile(@NotNull Profile profile) {
    myProfiles.put(profile.getName(), profile);
    for (ProfileChangeAdapter profileChangeAdapter : myProfilesListener) {
      profileChangeAdapter.profileChanged(profile);
    }

    initProfileWrapper(profile);
  }

  @Override
  public synchronized void deleteProfile(@NotNull String name) {
    myProfiles.remove(name);

    final InspectionProfileWrapper profileWrapper = myName2Profile.remove(name);
    if (profileWrapper != null) {
      profileWrapper.cleanup(myProject);
    }
  }

  @Override
  public void projectOpened() {
    StartupManager startupManager = StartupManager.getInstance(myProject);
    if (startupManager == null) {
      return; // upsource
    }
    startupManager.registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        Profile profile = getInspectionProfile();
        final Application app = ApplicationManager.getApplication();
        Runnable initInspectionProfilesRunnable = () -> {
          initProfileWrapper(profile);
          fireProfilesInitialized();
        };
        if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
          initInspectionProfilesRunnable.run();
          //do not restart daemon in the middle of the test
          //noinspection TestOnlyProblems
          UIUtil.dispatchAllInvocationEvents();
        }
        else {
          app.executeOnPooledThread(initInspectionProfilesRunnable);
        }
        myScopeListener = new NamedScopesHolder.ScopeListener() {
          @Override
          public void scopesChanged() {
            for (Profile profile : getProfiles()) {
              ((InspectionProfile)profile).scopesChanged();
            }
          }
        };
        myHolder.addScopeListener(myScopeListener);
        myLocalScopesHolder.addScopeListener(myScopeListener);
        Disposer.register(myProject, new Disposable() {
          @Override
          public void dispose() {
            myHolder.removeScopeListener(myScopeListener);
            myLocalScopesHolder.removeScopeListener(myScopeListener);
          }
        });
      }
    });
  }

  @Override
  public void initProfileWrapper(@NotNull Profile profile) {
    InspectionProfileWrapper wrapper = new InspectionProfileWrapper((InspectionProfile)profile);
    if (profile instanceof InspectionProfileImpl) {
      ((InspectionProfileImpl)profile).initInspectionTools(myProject);
    }
    if (profile.getProfileManager() == this) {
      myName2Profile.put(profile.getName(), wrapper);
    }
    else {
      myAppName2Profile.put(profile.getName(), wrapper);
    }
  }

  @Override
  public void projectClosed() {
    final Application app = ApplicationManager.getApplication();
    Runnable cleanupInspectionProfilesRunnable = () -> {
      for (InspectionProfileWrapper wrapper : myName2Profile.values()) {
        wrapper.cleanup(myProject);
      }
      for (InspectionProfileWrapper wrapper : myAppName2Profile.values()) {
        wrapper.cleanup(myProject);
      }
      fireProfilesShutdown();
    };
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      cleanupInspectionProfilesRunnable.run();
    }
    else {
      app.executeOnPooledThread(cleanupInspectionProfilesRunnable);
    }
  }

  @NotNull
  @Override
  public SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  @NotNull
  @Override
  public SeverityRegistrar getOwnSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  @Override
  public synchronized void loadState(Element state) {
    try {
      mySeverityRegistrar.readExternal(state);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    final Set<String> profileKeys = new THashSet<String>();
    profileKeys.addAll(myProfiles.keySet());
    myProfiles.clear();
    XmlSerializer.deserializeInto(this, state);
    for (Element o : state.getChildren(PROFILE)) {
      Profile profile = myApplicationProfileManager.createProfile();
      profile.setProfileManager(this);
      profile.readExternal(o);
      profile.setProjectLevel(true);
      if (profileKeys.contains(profile.getName())) {
        updateProfile(profile);
      }
      else {
        myProfiles.put(profile.getName(), profile);
      }
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

  @Override
  public synchronized Element getState() {
    Element state = new Element("settings");

    String[] sortedProfiles = myProfiles.keySet().toArray(new String[myProfiles.size()]);
    Arrays.sort(sortedProfiles);
    for (String profile : sortedProfiles) {
      final Profile projectProfile = myProfiles.get(profile);
      if (projectProfile != null) {
        Element profileElement = ProfileEx.serializeProfile(projectProfile);
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

    mySeverityRegistrar.writeExternal(state);
    return state;
  }

  private boolean isCustomProfileUsed() {
    return myProjectProfile != null && !Comparing.strEqual(myProjectProfile, PROJECT_DEFAULT_PROFILE_NAME);
  }

  @Override
  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }

  @NotNull
  @Override
  public NamedScopesHolder getScopesManager() {
    return myHolder;
  }

  @NotNull
  @Override
  public synchronized Collection<Profile> getProfiles() {
    getInspectionProfile();
    return myProfiles.values();
  }

  @NotNull
  @Override
  public synchronized String[] getAvailableProfileNames() {
    return ArrayUtil.toStringArray(myProfiles.keySet());
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
    useProjectProfile = newProfile != null;
    if (oldProfile != null) {
      for (ProfileChangeAdapter adapter : myProfilesListener) {
        adapter.profileActivated(getProfile(oldProfile), newProfile != null ? getProfile(newProfile) : null);
      }
    }
  }

  @NotNull
  public synchronized InspectionProfile getInspectionProfile() {
    if (!useProjectProfile) {
      return (InspectionProfile)myApplicationProfileManager.getRootProfile();
    }
    if (myProjectProfile == null || myProfiles.isEmpty()) {
      setProjectProfile(PROJECT_DEFAULT_PROFILE_NAME);
      final Profile projectProfile = myApplicationProfileManager.createProfile();
      projectProfile.copyFrom(myApplicationProfileManager.getRootProfile());
      projectProfile.setProjectLevel(true);
      projectProfile.setName(PROJECT_DEFAULT_PROFILE_NAME);
      myProfiles.put(PROJECT_DEFAULT_PROFILE_NAME, projectProfile);
    }
    else if (!myProfiles.containsKey(myProjectProfile)) {
      setProjectProfile(myProfiles.keySet().iterator().next());
    }
    final Profile profile = myProfiles.get(myProjectProfile);
    if (profile.isProjectLevel()) {
      profile.setProfileManager(this);
    }
    return (InspectionProfile)profile;
  }

  public void addProfilesListener(@NotNull ProfileChangeAdapter profilesListener, @NotNull Disposable parent) {
    myProfilesListener.add(profilesListener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myProfilesListener.remove(profilesListener);
      }
    });
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

  @Override
  public synchronized Profile getProfile(@NotNull String name, boolean returnRootProfileIfNamedIsAbsent) {
    Profile profile = myProfiles.get(name);
    return profile == null ? myApplicationProfileManager.getProfile(name, returnRootProfileIfNamedIsAbsent) : profile;
  }

  public void convert(Element element) {
    if (getProjectProfile() != null) {
      ((ProfileEx)getInspectionProfile()).convert(element, getProject());
    }
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

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "InspectionProjectProfileManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }
}
