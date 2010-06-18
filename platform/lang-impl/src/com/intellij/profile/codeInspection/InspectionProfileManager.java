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

import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public class InspectionProfileManager extends ApplicationProfileManager implements SeverityProvider, ExportableApplicationComponent, JDOMExternalizable {
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";

  private final InspectionToolRegistrar myRegistrar;
  private final SchemesManager<Profile, InspectionProfileImpl> mySchemesManager;
  private final AtomicBoolean myProfilesAreInitialized = new AtomicBoolean(false);
  private final SeverityRegistrar mySeverityRegistrar;
  @NonNls private static final String INSPECTION_DIR = "inspection";
  @NonNls private static final String FILE_SPEC = "$ROOT_CONFIG$/" + INSPECTION_DIR;

  private final List<ProfileChangeAdapter> myProfileChangeAdapters = new ArrayList<ProfileChangeAdapter>();

  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProfileManager");

  public static InspectionProfileManager getInstance() {
    return ServiceManager.getService(InspectionProfileManager.class);
  }

  public InspectionProfileManager(InspectionToolRegistrar registrar, SchemesManagerFactory schemesManagerFactory) {
    myRegistrar = registrar;
    mySeverityRegistrar = new SeverityRegistrar();
    SchemeProcessor<InspectionProfileImpl> processor = new BaseSchemeProcessor<InspectionProfileImpl>() {
      public InspectionProfileImpl readScheme(final Document document) {
        InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(document), myRegistrar, InspectionProfileManager.this);
        profile.load(document.getRootElement());
        return profile;
      }

      public boolean shouldBeSaved(final InspectionProfileImpl scheme) {
        return scheme.wasInitialized();
      }


      public Document writeScheme(final InspectionProfileImpl scheme) throws WriteExternalException {
        return scheme.saveToDocument();
      }

      public void onSchemeAdded(final InspectionProfileImpl scheme) {
        updateProfileImpl(scheme);
        fireProfileChanged(scheme);
        onProfilesChanged();
      }

      public void onSchemeDeleted(final InspectionProfileImpl scheme) {
        onProfilesChanged();
      }

      public void onCurrentSchemeChanged(final Scheme oldCurrentScheme) {
        Profile current = mySchemesManager.getCurrentScheme();
        if (current != null) {
          fireProfileChanged((Profile)oldCurrentScheme, current, null);
        }
        onProfilesChanged();
      }
    };

    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, processor, RoamingType.PER_USER);

  }

  private static InspectionProfileImpl createSampleProfile() {
    return new InspectionProfileImpl("Default");
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getProfileDirectory()};
  }

  @NotNull
  public String getPresentableName() {
    return InspectionsBundle.message("inspection.profiles.presentable.name");
  }

  public Collection<Profile> getProfiles() {
    initProfiles();
    return mySchemesManager.getAllSchemes();
  }

  private volatile boolean LOAD_PROFILES = !ApplicationManager.getApplication().isUnitTestMode();
  @TestOnly
  public void forceInitProfiles(boolean flag) {
    LOAD_PROFILES = flag;
    myProfilesAreInitialized.set(false);
  }

  public void initProfiles() {
    if (myProfilesAreInitialized.getAndSet(true)) {
      return;
    }
    if (!LOAD_PROFILES) return;

    mySchemesManager.loadSchemes();
    final Collection<Profile> profiles = mySchemesManager.getAllSchemes();

    if (profiles.isEmpty()) {
      createDefaultProfile();
    }
    else {
      for (Profile profile : profiles) {
        addProfile(profile);
      }
    }
  }

  public void createDefaultProfile() {
    final InspectionProfileImpl defaultProfile = (InspectionProfileImpl)createProfile();
    defaultProfile.setBaseProfile(InspectionProfileImpl.getDefaultProfile());
    addProfile(defaultProfile);
  }


  public Profile loadProfile(String path) throws IOException, JDOMException {
    final File file = new File(path);
    if (file.exists()){
      InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), myRegistrar, this);
      profile.load(JDOMUtil.loadDocument(file).getRootElement());
      return profile;
    }
    return getProfile(path, false);
  }

  private static String getProfileName(Document document) {
    String name = getRootElementAttribute(document, PROFILE_NAME_TAG);
    if (name != null) return name;
    return "unnamed";
  }

  private static String getProfileName(File file) {
    String name = getRootElementAttribute(file, PROFILE_NAME_TAG);
    if (name != null) return name;
    return FileUtil.getNameWithoutExtension(file);
  }

  private static String getRootElementAttribute(final Document document, @NonNls String name) {
    Element root = document.getRootElement();
    return root.getAttributeValue(name);
  }

  @Nullable
  private static String getRootElementAttribute(final File file, @NonNls String name) {
    try {
      Document doc = JDOMUtil.loadDocument(file);
      return getRootElementAttribute(doc, name);
    }
    catch (JDOMException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

 /*
   @NonNls private static final String BASE_PROFILE_ATTR = "base_profile";
   private static String getBaseProfileName(File file) throws JDOMException, IOException {
    return getRootElementAttribute(file, BASE_PROFILE_ATTR);
  }*/

  @NotNull
  public String getComponentName() {
    return "InspectionProfileManager";
  }


  public void updateProfile(Profile profile) {
    mySchemesManager.addNewScheme(profile, true);
    updateProfileImpl(profile);
  }

  private static void updateProfileImpl(final Profile profile) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      InspectionProjectProfileManager.getInstance(project).initProfileWrapper(profile);
    }
  }

  public SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public SeverityRegistrar getOwnSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    mySeverityRegistrar.readExternal(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    mySeverityRegistrar.writeExternal(element);
  }

  public InspectionProfileConvertor getConverter() {
    return new InspectionProfileConvertor(this);
  }

  public Profile createProfile() {
    return createSampleProfile();
  }

  public void addProfileChangeListener(final ProfileChangeAdapter listener) {
    myProfileChangeAdapters.add(listener);
  }

  public void addProfileChangeListener(ProfileChangeAdapter listener, Disposable parentDisposable) {
    ContainerUtil.add(listener, myProfileChangeAdapters, parentDisposable);
  }

  public void removeProfileChangeListener(final ProfileChangeAdapter listener) {
    myProfileChangeAdapters.remove(listener);
  }

  public void fireProfileChanged(final Profile profile) {
    for (ProfileChangeAdapter adapter : myProfileChangeAdapters) {
      adapter.profileChanged(profile);
    }
  }

  public void fireProfileChanged(final Profile oldProfile, final Profile profile, final NamedScope scope) {
    for (ProfileChangeAdapter adapter : myProfileChangeAdapters) {
      adapter.profileActivated(oldProfile, profile);
    }
  }

  public void setRootProfile(String rootProfile) {
    Profile current = mySchemesManager.getCurrentScheme();
    if (current != null && !Comparing.strEqual(rootProfile, current.getName())) {
      fireProfileChanged(current, getProfile(rootProfile), null);
    }
    mySchemesManager.setCurrentSchemeName(rootProfile);
  }


  public Profile getProfile(@NotNull final String name, boolean returnRootProfileIfNamedIsAbsent) {
    Profile found = mySchemesManager.findSchemeByName(name);
    if (found != null) return found;
    //profile was deleted
    if (returnRootProfileIfNamedIsAbsent) {
      return getRootProfile();
    }
    else {
      return null;
    }
  }

  public Profile getRootProfile() {
    Profile current = mySchemesManager.getCurrentScheme();
    if (current != null) return current;
    Collection<Profile> profiles = getProfiles();
    if (profiles.isEmpty()) return createSampleProfile();
    return profiles.iterator().next();
  }

  public void deleteProfile(final String profile) {
    Profile found = mySchemesManager.findSchemeByName(profile);
    if (found != null) {
      mySchemesManager.removeScheme(found);
    }
  }

  public void addProfile(final Profile profile) {
    mySchemesManager.addNewScheme(profile, true);
  }

  @Nullable
  public static File getProfileDirectory() {
    String directoryPath = PathManager.getConfigPath() + File.separator + INSPECTION_DIR;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!directory.mkdir()) {
        return null;
      }
    }
    return directory;
  }

  public String[] getAvailableProfileNames() {
    final Collection<String> names = mySchemesManager.getAllSchemeNames();
    return ArrayUtil.toStringArray(names);
  }

  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }

  public SchemesManager<Profile, InspectionProfileImpl> getSchemesManager() {
    return mySchemesManager;
  }

  public void onProfilesChanged() {
    //cleanup caches blindly for all projects in case ide profile was modified
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      HighlightingSettingsPerFile.getInstance(project).cleanProfileSettings();

      InspectionProjectProfileManager.getInstance(project).updateStatusBar();

    }



  }
}
