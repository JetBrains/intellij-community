/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileEx;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
@State(
  name = "InspectionProjectProfileManager",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/inspectionProfiles/", scheme = StorageScheme.DIRECTORY_BASED,
             stateSplitter = InspectionProjectProfileManagerImpl.ProfileStateSplitter.class)
  }
)
public class InspectionProjectProfileManagerImpl extends InspectionProjectProfileManager implements ProjectComponent, PersistentStateComponent<Element> {
  private final Map<String, InspectionProfileWrapper>  myName2Profile = new ConcurrentHashMap<String, InspectionProfileWrapper>();
  private final SeverityRegistrar mySeverityRegistrar;
  private final NamedScopeManager myLocalScopesHolder;
  private NamedScopesHolder.ScopeListener myScopeListener;

  public InspectionProjectProfileManagerImpl(@NotNull Project project,
                                             @NotNull InspectionProfileManager inspectionProfileManager,
                                             @NotNull DependencyValidationManager holder,
                                             @NotNull NamedScopeManager localScopesHolder) {
    super(project, inspectionProfileManager, holder);
    myLocalScopesHolder = localScopesHolder;
    mySeverityRegistrar = new SeverityRegistrar();
  }

  public static InspectionProjectProfileManagerImpl getInstanceImpl(Project project){
    return (InspectionProjectProfileManagerImpl)project.getComponent(InspectionProjectProfileManager.class);
  }

  @Override
  public Element getState() {
    try {
      final Element e = new Element("settings");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  @Override
  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isProfileLoaded() {
    return myName2Profile.containsKey(getInspectionProfile().getName());
  }

  @NotNull
  public synchronized InspectionProfileWrapper getProfileWrapper(){
    final InspectionProfile profile = getInspectionProfile();
    final String profileName = profile.getName();
    if (!myName2Profile.containsKey(profileName)){
      initProfileWrapper(profile);
    }
    return myName2Profile.get(profileName);
  }

  public InspectionProfileWrapper getProfileWrapper(final String profileName){
    return myName2Profile.get(profileName);
  }

  @Override
  public void updateProfile(@NotNull Profile profile) {
    super.updateProfile(profile);
    initProfileWrapper(profile);
  }

  @Override
  public void deleteProfile(String name) {
    super.deleteProfile(name);
    final InspectionProfileWrapper profileWrapper = myName2Profile.remove(name);
    if (profileWrapper != null) {
      profileWrapper.cleanup(myProject);
    }
  }

  @Override
  public void projectOpened() {
    StartupManager startupManager = StartupManager.getInstance(myProject);
    if (startupManager == null) return; // upsource
    startupManager.registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        final Set<Profile> profiles = new HashSet<Profile>();
        profiles.add(getProjectProfileImpl());
        profiles.addAll(getProfiles());
        profiles.addAll(InspectionProfileManager.getInstance().getProfiles());
        final Application app = ApplicationManager.getApplication();
        Runnable initInspectionProfilesRunnable = new Runnable() {
          @Override
          public void run() {
            for (Profile profile : profiles) {
              initProfileWrapper(profile);
            }
            fireProfilesInitialized();
          }
        };
        if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
          initInspectionProfilesRunnable.run();
          UIUtil.dispatchAllInvocationEvents(); //do not restart daemon in the middle of the test
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
    final InspectionProfileWrapper wrapper = new InspectionProfileWrapper((InspectionProfile)profile);
    wrapper.init(myProject);
    myName2Profile.put(profile.getName(), wrapper);
  }

  @Override
  public void projectClosed() {
    final Application app = ApplicationManager.getApplication();
    Runnable cleanupInspectionProfilesRunnable = new Runnable() {
      @Override
      public void run() {
        for (InspectionProfileWrapper wrapper : myName2Profile.values()) {
          wrapper.cleanup(myProject);
        }
        fireProfilesShutdown();
      }
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
  public void readExternal(final Element element) throws InvalidDataException {
    mySeverityRegistrar.readExternal(element);
    super.readExternal(element);
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    mySeverityRegistrar.writeExternal(element);
  }

  @Override
  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }

  @Override
  public void convert(Element element) throws InvalidDataException {
    super.convert(element);
    if (PROJECT_PROFILE != null) {
      ((ProfileEx)getProjectProfileImpl()).convert(element, getProject());
    }
  }
}
