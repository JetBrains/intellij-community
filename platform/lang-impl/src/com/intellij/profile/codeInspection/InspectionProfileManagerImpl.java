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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.profile.Profile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public class InspectionProfileManagerImpl extends InspectionProfileManager implements SeverityProvider, ExportableComponent, JDOMExternalizable,
                                                                                   NamedComponent {

  private final InspectionToolRegistrar myRegistrar;
  private final SchemesManager<Profile, InspectionProfileImpl> mySchemesManager;
  private final AtomicBoolean myProfilesAreInitialized = new AtomicBoolean(false);
  private final SeverityRegistrar mySeverityRegistrar;

  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProfileManager");

  public static InspectionProfileManagerImpl getInstanceImpl() {
    return (InspectionProfileManagerImpl)ServiceManager.getService(InspectionProfileManager.class);
  }

  public InspectionProfileManagerImpl(InspectionToolRegistrar registrar, SchemesManagerFactory schemesManagerFactory) {
    myRegistrar = registrar;
    mySeverityRegistrar = new SeverityRegistrar();
    registerProvidedSeverities();

    SchemeProcessor<InspectionProfileImpl> processor = new BaseSchemeProcessor<InspectionProfileImpl>() {
      @Override
      public InspectionProfileImpl readScheme(final Document document) {
        InspectionProfileImpl profile = new InspectionProfileImpl(InspectionProfileLoadUtil.getProfileName(document), myRegistrar, InspectionProfileManagerImpl.this);
        read(profile, document.getRootElement());
        return profile;
      }

      @Override
      public boolean shouldBeSaved(final InspectionProfileImpl scheme) {
        return scheme.wasInitialized();
      }


      @Override
      public Document writeScheme(final InspectionProfileImpl scheme) throws WriteExternalException {
        return scheme.saveToDocument();
      }

      @Override
      public void onSchemeAdded(final InspectionProfileImpl scheme) {
        updateProfileImpl(scheme);
        fireProfileChanged(scheme);
        onProfilesChanged();
      }

      @Override
      public void onSchemeDeleted(final InspectionProfileImpl scheme) {
        onProfilesChanged();
      }

      @Override
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

  private static void read(@NotNull final InspectionProfileImpl profile, @NotNull Element element) {
    try {
      profile.readExternal(element);
    }
    catch (Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", 0, profile.getName()),
                                   InspectionsBundle.message("inspection.errors.occurred.dialog.title"));
        }
      }, ModalityState.NON_MODAL);
    }
  }

  @NotNull
  private static InspectionProfileImpl createSampleProfile() {
    return new InspectionProfileImpl("Default");
  }

  public static void registerProvidedSeverities() {
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
      for (HighlightInfoType highlightInfoType : provider.getSeveritiesHighlightInfoTypes()) {
        final HighlightSeverity highlightSeverity = highlightInfoType.getSeverity(null);
        SeverityRegistrar.registerStandard(highlightInfoType, highlightSeverity);
        final TextAttributesKey attributesKey = highlightInfoType.getAttributesKey();
        TextAttributes textAttributes = scheme.getAttributes(attributesKey);
        if (textAttributes == null) {
          textAttributes = attributesKey.getDefaultAttributes();
        }
        HighlightDisplayLevel.registerSeverity(highlightSeverity, provider.getTrafficRendererColor(textAttributes));
      }
    }
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{getProfileDirectory()};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return InspectionsBundle.message("inspection.profiles.presentable.name");
  }

  @Override
  @NotNull
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

  @Override
  public void initProfiles() {
    if (myProfilesAreInitialized.getAndSet(true)) {
      if (mySchemesManager.getAllSchemeNames().isEmpty()) {
        createDefaultProfile();
      }
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

  private void createDefaultProfile() {
    final InspectionProfileImpl defaultProfile = (InspectionProfileImpl)createProfile();
    defaultProfile.setBaseProfile(InspectionProfileImpl.getDefaultProfile());
    addProfile(defaultProfile);
  }


  @Override
  public Profile loadProfile(@NotNull String path) throws IOException, JDOMException {
    final File file = new File(path);
    if (file.exists()){
      try {
        return InspectionProfileLoadUtil.load(file, myRegistrar, this);
      }
      catch (IOException e) {
        throw e;
      }
      catch (JDOMException e) {
        throw e;
      }
      catch (Exception e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", 0, file),
                                     InspectionsBundle.message("inspection.errors.occurred.dialog.title"));
          }
        }, ModalityState.NON_MODAL);
      }
    }
    return getProfile(path, false);
  }

  @Override
  public void updateProfile(@NotNull Profile profile) {
    mySchemesManager.addNewScheme(profile, true);
    updateProfileImpl(profile);
  }

  private static void updateProfileImpl(@NotNull Profile profile) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      InspectionProjectProfileManager.getInstance(project).initProfileWrapper(profile);
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
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    mySeverityRegistrar.writeExternal(element);
  }

  public InspectionProfileConvertor getConverter() {
    return new InspectionProfileConvertor(this);
  }

  @Override
  public Profile createProfile() {
    return createSampleProfile();
  }

  @Override
  public void setRootProfile(String rootProfile) {
    Profile current = mySchemesManager.getCurrentScheme();
    if (current != null && !Comparing.strEqual(rootProfile, current.getName())) {
      fireProfileChanged(current, getProfile(rootProfile), null);
    }
    mySchemesManager.setCurrentSchemeName(rootProfile);
  }


  @Override
  public Profile getProfile(@NotNull final String name, boolean returnRootProfileIfNamedIsAbsent) {
    Profile found = mySchemesManager.findSchemeByName(name);
    if (found != null) return found;
    //profile was deleted
    if (returnRootProfileIfNamedIsAbsent) {
      return getRootProfile();
    }
    return null;
  }

  @NotNull
  @Override
  public Profile getRootProfile() {
    Profile current = mySchemesManager.getCurrentScheme();
    if (current != null) return current;
    Collection<Profile> profiles = getProfiles();
    if (profiles.isEmpty()) return createSampleProfile();
    return profiles.iterator().next();
  }

  @Override
  public void deleteProfile(final String profile) {
    Profile found = mySchemesManager.findSchemeByName(profile);
    if (found != null) {
      mySchemesManager.removeScheme(found);
    }
  }

  @Override
  public void addProfile(@NotNull final Profile profile) {
    mySchemesManager.addNewScheme(profile, true);
  }

  @Override
  @NotNull
  public String[] getAvailableProfileNames() {
    final Collection<String> names = mySchemesManager.getAllSchemeNames();
    return ArrayUtil.toStringArray(names);
  }

  @Override
  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }

  @NotNull
  public SchemesManager<Profile, InspectionProfileImpl> getSchemesManager() {
    return mySchemesManager;
  }

  public static void onProfilesChanged() {
    //cleanup caches blindly for all projects in case ide profile was modified
    for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
      synchronized (HighlightingSettingsPerFile.getInstance(project)) {
      }

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (!project.isDisposed()) {
            DaemonListeners.getInstance(project).updateStatusBar();
          }
        }
      });
    }
  }
}
