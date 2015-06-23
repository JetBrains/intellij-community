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
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileLoadUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

@State(
  name = "InspectionProfileManager",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/editor.xml"),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true)
  },
  additionalExportFile = InspectionProfileManager.FILE_SPEC
)
public class InspectionProfileManagerImpl extends InspectionProfileManager implements SeverityProvider, PersistentStateComponent<Element> {
  private final InspectionToolRegistrar myRegistrar;
  private final SchemesManager<Profile, InspectionProfileImpl> mySchemesManager;
  private final AtomicBoolean myProfilesAreInitialized = new AtomicBoolean(false);
  private final SeverityRegistrar mySeverityRegistrar;

  protected static final Logger LOG = Logger.getInstance("#com.intellij.profile.DefaultProfileManager");

  public static InspectionProfileManagerImpl getInstanceImpl() {
    return (InspectionProfileManagerImpl)ServiceManager.getService(InspectionProfileManager.class);
  }

  public InspectionProfileManagerImpl(@NotNull InspectionToolRegistrar registrar, @NotNull SchemesManagerFactory schemesManagerFactory, @NotNull MessageBus messageBus) {
    myRegistrar = registrar;
    registerProvidedSeverities();

    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, new BaseSchemeProcessor<InspectionProfileImpl>() {
      @NotNull
      @Override
      public InspectionProfileImpl readScheme(@NotNull Element element) {
        final InspectionProfileImpl profile = new InspectionProfileImpl(InspectionProfileLoadUtil.getProfileName(element), myRegistrar, InspectionProfileManagerImpl.this);
        try {
          profile.readExternal(element);
        }
        catch (Exception ignored) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", 0, profile.getName()),
                                       InspectionsBundle.message("inspection.errors.occurred.dialog.title"));
            }
          }, ModalityState.NON_MODAL);
        }
        return profile;
      }

      @NotNull
      @Override
      public State getState(@NotNull InspectionProfileImpl scheme) {
        return scheme.isProjectLevel() ? State.NON_PERSISTENT : (scheme.wasInitialized() ? State.POSSIBLY_CHANGED : State.UNCHANGED);
      }

      @Override
      public Element writeScheme(@NotNull InspectionProfileImpl scheme) {
        Element root = new Element("inspections");
        root.setAttribute("profile_name", scheme.getName());
        scheme.serializeInto(root, false);
        return root;
      }

      @Override
      public void onSchemeAdded(@NotNull final InspectionProfileImpl scheme) {
        updateProfileImpl(scheme);
        fireProfileChanged(scheme);
        onProfilesChanged();
      }

      @Override
      public void onSchemeDeleted(@NotNull final InspectionProfileImpl scheme) {
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
    }, RoamingType.PER_USER);
    mySeverityRegistrar = new SeverityRegistrar(messageBus);
  }

  @NotNull
  private InspectionProfileImpl createSampleProfile(@NotNull String name, InspectionProfileImpl baseProfile) {
    return new InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this, baseProfile);
  }

  public static void registerProvidedSeverities() {
    for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
      for (HighlightInfoType t : provider.getSeveritiesHighlightInfoTypes()) {
        HighlightSeverity highlightSeverity = t.getSeverity(null);
        SeverityRegistrar.registerStandard(t, highlightSeverity);
        TextAttributesKey attributesKey = t.getAttributesKey();
        Icon icon = t instanceof HighlightInfoType.Iconable ? ((HighlightInfoType.Iconable)t).getIcon() : null;
        HighlightDisplayLevel.registerSeverity(highlightSeverity, attributesKey, icon);
      }
    }
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
      if (mySchemesManager.getAllSchemes().isEmpty()) {
        createDefaultProfile();
      }
      return;
    }
    if (!LOAD_PROFILES) return;

    mySchemesManager.loadSchemes();
    Collection<Profile> profiles = mySchemesManager.getAllSchemes();
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
    final InspectionProfileImpl defaultProfile = createSampleProfile(InspectionProfileImpl.DEFAULT_PROFILE_NAME, InspectionProfileImpl.getDefaultProfile());
    addProfile(defaultProfile);
  }


  @Override
  public Profile loadProfile(@NotNull String path) throws IOException, JDOMException {
    final File file = new File(path);
    if (file.exists()) {
      try {
        return InspectionProfileLoadUtil.load(file, myRegistrar, this);
      }
      catch (IOException e) {
        throw e;
      }
      catch (JDOMException e) {
        throw e;
      }
      catch (Exception ignored) {
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
    mySchemesManager.addScheme(profile);
    updateProfileImpl(profile);
  }

  private static void updateProfileImpl(@NotNull Profile profile) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
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

  @Nullable
  @Override
  public Element getState() {
    Element state = new Element("state");
    try {
      mySeverityRegistrar.writeExternal(state);
    }
    catch (WriteExternalException e) {
      throw new RuntimeException(e);
    }
    return state;
  }

  @Override
  public void loadState(Element state) {
    try {
      mySeverityRegistrar.readExternal(state);
    }
    catch (InvalidDataException e) {
      throw new RuntimeException(e);
    }
  }

  public InspectionProfileConvertor getConverter() {
    return new InspectionProfileConvertor(this);
  }

  @Override
  public InspectionProfileImpl createProfile() {
    return createSampleProfile(InspectionProfileImpl.DEFAULT_PROFILE_NAME, InspectionProfileImpl.getDefaultProfile());
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
    if (profiles.isEmpty()) return createSampleProfile(InspectionProfileImpl.DEFAULT_PROFILE_NAME, null);
    return profiles.iterator().next();
  }

  @Override
  public void deleteProfile(@NotNull final String profile) {
    Profile found = mySchemesManager.findSchemeByName(profile);
    if (found != null) {
      mySchemesManager.removeScheme(found);
    }
  }

  @Override
  public void addProfile(@NotNull final Profile profile) {
    mySchemesManager.addScheme(profile);
  }

  @Override
  @NotNull
  public String[] getAvailableProfileNames() {
    return ArrayUtil.toStringArray(mySchemesManager.getAllSchemeNames());
  }

  @Override
  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }

  public static void onProfilesChanged() {
    //cleanup caches blindly for all projects in case ide profile was modified
    for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
      //noinspection EmptySynchronizedStatement
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
