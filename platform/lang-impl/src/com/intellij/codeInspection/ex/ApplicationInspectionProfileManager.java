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
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.intellij.codeInspection.ex.InspectionProfileImpl.getDefaultProfile;

@State(
  name = "InspectionProfileManager",
  storages = {
    @Storage("editor.xml"),
    @Storage(value = "other.xml", deprecated = true)
  },
  additionalExportFile = InspectionProfileManager.INSPECTION_DIR
)
public class ApplicationInspectionProfileManager extends BaseInspectionProfileManager implements InspectionProfileManager,
                                                                                                 SeverityProvider,
                                                                                                 PersistentStateComponent<Element> {
  private final InspectionToolRegistrar myRegistrar;
  private final SchemeManager<InspectionProfileImpl> mySchemeManager;
  private final AtomicBoolean myProfilesAreInitialized = new AtomicBoolean(false);

  public static ApplicationInspectionProfileManager getInstanceImpl() {
    return (ApplicationInspectionProfileManager)ServiceManager.getService(InspectionProfileManager.class);
  }

  public ApplicationInspectionProfileManager(@NotNull InspectionToolRegistrar registrar, @NotNull SchemeManagerFactory schemeManagerFactory, @NotNull MessageBus messageBus) {
    super(messageBus);

    myRegistrar = registrar;
    registerProvidedSeverities();

    mySchemeManager = schemeManagerFactory.create(INSPECTION_DIR, new InspectionProfileProcessor() {
      @NotNull
      @Override
      public String getName(@NotNull Function<String, String> attributeProvider) {
        return ObjectUtils.chooseNotNull(attributeProvider.apply(InspectionProfileLoadUtil.PROFILE_NAME_TAG), "unnamed");
      }

      @NotNull
      public InspectionProfileImpl createScheme(@NotNull SchemeDataHolder<? super InspectionProfileImpl> dataHolder,
                                                @NotNull String name,
                                                @NotNull Function<String, String> attributeProvider) {
        return new InspectionProfileImpl(name, myRegistrar, ApplicationInspectionProfileManager.this, getDefaultProfile(), dataHolder);
      }

      @Override
      public void onSchemeAdded(@NotNull InspectionProfileImpl scheme) {
        fireProfileChanged(scheme);
        onProfilesChanged();
      }

      @Override
      public void onSchemeDeleted(@NotNull InspectionProfileImpl scheme) {
        onProfilesChanged();
      }

      @Override
      public void onCurrentSchemeSwitched(@Nullable InspectionProfileImpl oldScheme, @Nullable InspectionProfileImpl newScheme) {
        if (newScheme != null) {
          fireProfileChanged(oldScheme, newScheme);
        }
        onProfilesChanged();
      }
    });
  }

  @NotNull
  @Override
  protected SchemeManager<InspectionProfileImpl> getSchemeManager() {
    return mySchemeManager;
  }

  @NotNull
  private InspectionProfileImpl createSampleProfile(@NotNull String name, InspectionProfileImpl baseProfile) {
    return new InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this, baseProfile, null);
  }

  // It should be public to be available from Upsource
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
    return Collections.unmodifiableList(mySchemeManager.getAllSchemes());
  }

  private volatile boolean LOAD_PROFILES = !ApplicationManager.getApplication().isUnitTestMode();
  @TestOnly
  public void forceInitProfiles(boolean flag) {
    LOAD_PROFILES = flag;
    myProfilesAreInitialized.set(false);
  }

  public void initProfiles() {
    if (myProfilesAreInitialized.getAndSet(true)) {
      if (mySchemeManager.isEmpty()) {
        createDefaultProfile();
      }
      return;
    }
    if (!LOAD_PROFILES) return;

    mySchemeManager.loadSchemes();
    if (mySchemeManager.isEmpty()) {
      createDefaultProfile();
    }
  }

  private void createDefaultProfile() {
    getSchemeManager().addScheme(createSampleProfile(InspectionProfileImpl.DEFAULT_PROFILE_NAME, getDefaultProfile()));
  }

  public Profile loadProfile(@NotNull String path) throws IOException, JDOMException {
    final File file = new File(path);
    if (file.exists()) {
      try {
        return InspectionProfileLoadUtil.load(file, myRegistrar, this);
      }
      catch (IOException | JDOMException e) {
        throw e;
      }
      catch (Exception ignored) {
        ApplicationManager.getApplication().invokeLater(() -> Messages
          .showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", 0, file),
                           InspectionsBundle.message("inspection.errors.occurred.dialog.title")), ModalityState.NON_MODAL);
      }
    }
    return getProfile(path, false);
  }

  @Override
  public void updateProfile(@NotNull Profile profile) {
    super.updateProfile(profile);
    updateProfileImpl((InspectionProfileImpl)profile);
  }

  private static void updateProfileImpl(@NotNull InspectionProfileImpl profile) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      profile.initInspectionTools(project);
    }
  }

  @Nullable
  @Override
  public Element getState() {
    Element state = new Element("state");
    getSeverityRegistrar().writeExternal(state);
    return state;
  }

  @Override
  public void loadState(Element state) {
    getSeverityRegistrar().readExternal(state);
  }

  public InspectionProfileConvertor getConverter() {
    return new InspectionProfileConvertor(this);
  }

  @SuppressWarnings("unused")
  @Deprecated
  public InspectionProfileImpl createProfile() {
    return createSampleProfile(InspectionProfileImpl.DEFAULT_PROFILE_NAME, getDefaultProfile());
  }

  @Override
  public void setRootProfile(@Nullable String profileName) {
    mySchemeManager.setCurrentSchemeName(profileName);
  }

  @Override
  public Profile getProfile(@NotNull final String name, boolean returnRootProfileIfNamedIsAbsent) {
    Profile found = mySchemeManager.findSchemeByName(name);
    if (found != null) return found;
    //profile was deleted
    if (returnRootProfileIfNamedIsAbsent) {
      return getCurrentProfile();
    }
    return null;
  }

  @NotNull
  @Override
  public InspectionProfile getCurrentProfile() {
    initProfiles();
    Profile current = mySchemeManager.getCurrentScheme();
    if (current != null) return (InspectionProfile)current;
    Collection<Profile> profiles = getProfiles();
    if (profiles.isEmpty()) return createSampleProfile(InspectionProfileImpl.DEFAULT_PROFILE_NAME, null);
    return (InspectionProfile)profiles.iterator().next();
  }

  @NotNull
  public String getRootProfileName() {
    return ObjectUtils.chooseNotNull(mySchemeManager.getCurrentSchemeName(), InspectionProfileImpl.DEFAULT_PROFILE_NAME);
  }

  @Override
  @NotNull
  public String[] getAvailableProfileNames() {
    return ArrayUtil.toStringArray(mySchemeManager.getAllSchemeNames());
  }

  public static void onProfilesChanged() {
    //cleanup caches blindly for all projects in case ide profile was modified
    for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
      //noinspection EmptySynchronizedStatement
      synchronized (HighlightingSettingsPerFile.getInstance(project)) {
      }

      UIUtil.invokeLaterIfNeeded(() -> {
        if (!project.isDisposed()) {
          DaemonListeners.getInstance(project).updateStatusBar();
        }
      });
    }
  }
}
