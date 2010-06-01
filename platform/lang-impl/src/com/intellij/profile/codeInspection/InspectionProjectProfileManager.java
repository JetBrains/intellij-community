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
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.*;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.startup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.*;
import com.intellij.openapi.wm.impl.status.*;
import com.intellij.packageDependencies.*;
import com.intellij.profile.*;
import com.intellij.psi.*;
import org.jdom.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
@State(
  name = "InspectionProjectProfileManager",
  storages = {
    @Storage(
      id ="default",
      file = "$PROJECT_FILE$"
    )
    ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/inspectionProfiles/", scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = InspectionProjectProfileManager.ProfileStateSplitter.class)
    }
)
public class InspectionProjectProfileManager extends DefaultProjectProfileManager implements SeverityProvider, ProjectComponent, PersistentStateComponent<Element> {
  private final Map<String, InspectionProfileWrapper>  myName2Profile = new HashMap<String, InspectionProfileWrapper>();
  private final SeverityRegistrar mySeverityRegistrar;
  private StatusBarEx myStatusBar;
  private TogglePopupHintsPanel myTogglePopupHintsPanel;

  public InspectionProjectProfileManager(final Project project, DependencyValidationManager holder) {
    super(project, Profile.INSPECTION, holder);
    mySeverityRegistrar = new SeverityRegistrar();
  }

  public static InspectionProjectProfileManager getInstance(Project project){
    return project.getComponent(InspectionProjectProfileManager.class);
  }

  public String getProfileName() {
    return getInspectionProfile().getName();
  }

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

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public InspectionProfile getInspectionProfile(){
    return (InspectionProfile)getProjectProfileImpl();
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @NotNull
  public InspectionProfile getInspectionProfile(PsiElement element){
    return getInspectionProfile();
  }

  public InspectionProfileWrapper getProfileWrapper(){
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

  public void updateProfile(Profile profile) {
    super.updateProfile(profile);
    initProfileWrapper(profile);
  }

  public void deleteProfile(String name) {
    super.deleteProfile(name);
    final InspectionProfileWrapper profileWrapper = myName2Profile.remove(name);
    LOG.assertTrue(profileWrapper != null, "Profile wasn't initialized" + name);
    profileWrapper.cleanup(myProject);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "InspectionProjectProfileManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    myStatusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
    myTogglePopupHintsPanel = new TogglePopupHintsPanel(myProject);
    myStatusBar.addWidget(myTogglePopupHintsPanel, myProject);
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        final Set<Profile> profiles = new HashSet<Profile>();
        profiles.add(getProjectProfileImpl());
        profiles.addAll(getProfiles());
        profiles.addAll(InspectionProfileManager.getInstance().getProfiles());
        final Application app = ApplicationManager.getApplication();
        Runnable initInspectionProfilesRunnable = new Runnable() {
          public void run() {
            for (Profile profile : profiles) {
              initProfileWrapper(profile);
            }
          }
        };
        if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
          initInspectionProfilesRunnable.run();
        } else {
          app.executeOnPooledThread(initInspectionProfilesRunnable);
        }
      }
    });
  }

  public void initProfileWrapper(final Profile profile) {
    final InspectionProfileWrapper wrapper = new InspectionProfileWrapper((InspectionProfile)profile);
    wrapper.init(myProject);
    myName2Profile.put(profile.getName(), wrapper);
  }

  public void projectClosed() {

    final Application app = ApplicationManager.getApplication();
    Runnable cleanupInspectionProfilesRunnable = new Runnable() {
      public void run() {
        for (InspectionProfileWrapper wrapper : myName2Profile.values()) {
          wrapper.cleanup(myProject);
        }
      }
    };
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      cleanupInspectionProfilesRunnable.run();
    }
    else {
      app.executeOnPooledThread(cleanupInspectionProfilesRunnable);
    }
    HighlightingSettingsPerFile.getInstance(myProject).cleanProfileSettings();
  }

  public SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public SeverityRegistrar getOwnSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    mySeverityRegistrar.readExternal(element);
    super.readExternal(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    mySeverityRegistrar.writeExternal(element);
  }

  public void updateStatusBar() {
    if (myTogglePopupHintsPanel != null) myTogglePopupHintsPanel.updateStatus();
  }

  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }

  public void convert(Element element) throws InvalidDataException {
    super.convert(element);
    if (PROJECT_PROFILE != null) {
      ((InspectionProfileImpl)getProjectProfileImpl()).convert(element);
    }
  }
}
