// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.MainConfigurationStateSplitter;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

@State(
  name = "ProjectCodeStyleConfiguration",
  storages = @Storage(value = "codeStyles", stateSplitter = ProjectCodeStyleSettingsManager.StateSplitter.class)
)
@ApiStatus.Internal
public final class ProjectCodeStyleSettingsManager extends CodeStyleSettingsManager {
  private static final Logger LOG = Logger.getInstance(ProjectCodeStyleSettingsManager.class);

  private static final String MAIN_PROJECT_CODE_STYLE_NAME = "Project";
  private static final String PROJECT_CODE_STYLE_CONFIG_FILE_NAME = "codeStyleConfig";

  private final Project myProject;
  private volatile boolean myIsLoaded;
  private final Map<String,CodeStyleSettings> mySettingsMap = new HashMap<>();
  private final Object myStateLock = new Object();

  public ProjectCodeStyleSettingsManager(@NotNull Project project) {
    myProject = project;
    setMainProjectCodeStyle(null);
    registerExtensionPointListeners(project);
  }

  @Override
  public void initializeComponent() {
    initProjectSettings(myProject);
    //noinspection deprecation
    getCurrentSettings(); // cache default scheme
  }

  private void initProjectSettings(@NotNull Project project) {
    synchronized (myStateLock) {
      if (!myIsLoaded) {
        LegacyCodeStyleSettingsManager legacySettingsManager = project.getService(LegacyCodeStyleSettingsManager.class);
        if (legacySettingsManager != null && legacySettingsManager.getState() != null) {
          loadState(legacySettingsManager.getState());
          if (!project.isDefault() &&
              !ApplicationManager.getApplication().isUnitTestMode() &&
              !ApplicationManager.getApplication().isHeadlessEnvironment()) {
            getMainProjectCodeStyle().getModificationTracker().incModificationCount();
            project.scheduleSave();
          }
          LOG.info("Imported old project code style settings.");
        }
        else {
          initDefaults();
          LOG.info("Initialized from default code style settings.");
        }
      }
    }
  }

  @Override
  public void setMainProjectCodeStyle(@Nullable CodeStyleSettings settings) {
    synchronized (myStateLock) {
      mySettingsMap.put(MAIN_PROJECT_CODE_STYLE_NAME, settings != null ? settings : createSettings());
    }
  }

  @Override
  public @NotNull CodeStyleSettings getMainProjectCodeStyle() {
    synchronized (myStateLock) {
      return mySettingsMap.get(MAIN_PROJECT_CODE_STYLE_NAME);
    }
  }

  @Override
  protected @NotNull MessageBus getMessageBus() {
    return myProject.getMessageBus();
  }

  private void initDefaults() {
    CodeStyleSettingsManager appCodeStyleSettingsManager = CodeStyleSettingsManager.getInstance();
    if (appCodeStyleSettingsManager != null) {
      CodeStyleSettings defaultProjectSettings = appCodeStyleSettingsManager.getMainProjectCodeStyle();
      setMainProjectCodeStyle(defaultProjectSettings != null ? cloneSettings(defaultProjectSettings) : null);
      USE_PER_PROJECT_SETTINGS = appCodeStyleSettingsManager.USE_PER_PROJECT_SETTINGS;
      PREFERRED_PROJECT_CODE_STYLE = appCodeStyleSettingsManager.PREFERRED_PROJECT_CODE_STYLE;
    }
  }

  @Override
  public void loadState(@NotNull Element state) {
    synchronized (myStateLock) {
      LOG.info("Loading Project code style");
      super.loadState(state);
      updateFromOldProjectSettings();
      for (Element subStyle : state.getChildren(CodeStyleScheme.CODE_STYLE_TAG_NAME)) {
        String name = subStyle.getAttributeValue(CodeStyleScheme.CODE_STYLE_NAME_ATTR);
        CodeStyleSettings settings = createSettings();
        settings.readExternal(subStyle);
        if (MAIN_PROJECT_CODE_STYLE_NAME.equals(name)) {
          setMainProjectCodeStyle(settings);
        }
        else {
          mySettingsMap.put(name, settings);
        }
        LOG.info(name + " code style loaded");
      }
      myIsLoaded = true;
    }
  }

  private void updateFromOldProjectSettings() {
    CodeStyleSettings oldProjectSettings = PER_PROJECT_SETTINGS;
    if (oldProjectSettings != null) oldProjectSettings.resetDeprecatedFields();
    setMainProjectCodeStyle(oldProjectSettings);
    PER_PROJECT_SETTINGS = null;
  }

  @Override
  public Element getState() {
    synchronized (myStateLock) {
      Element e = super.getState();
      if (e != null) {
        LOG.info("Saving Project code style");
        for (String name : mySettingsMap.keySet()) {
          CodeStyleSettings settings = mySettingsMap.get(name);
          Element codeStyle = new Element(CodeStyleScheme.CODE_STYLE_TAG_NAME);
          codeStyle.setAttribute(CodeStyleScheme.CODE_STYLE_NAME_ATTR, name);
          settings.writeExternal(codeStyle);
          if (!codeStyle.getContent().isEmpty()) {
            e.addContent(codeStyle);
            LOG.info(name + " code style saved");
          }
        }
      }
      return e;
    }
  }

  @Override
  protected boolean isIgnoredOnSave(@NotNull String fieldName) {
    return "PER_PROJECT_SETTINGS".equals(fieldName);
  }

  static final class StateSplitter extends MainConfigurationStateSplitter {
    @Override
    protected @NotNull String getComponentStateFileName() {
      return PROJECT_CODE_STYLE_CONFIG_FILE_NAME;
    }

    @Override
    protected @NotNull String getSubStateTagName() {
      return CodeStyleScheme.CODE_STYLE_TAG_NAME;
    }

    @Override
    protected @NotNull String getSubStateFileName(@NotNull Element element) {
      return Objects.requireNonNull(element.getAttributeValue(CodeStyleScheme.CODE_STYLE_NAME_ATTR));
    }
  }

  @Override
  protected @NotNull @Unmodifiable Collection<CodeStyleSettings> enumSettings() {
    return Collections.unmodifiableCollection(mySettingsMap.values());
  }

  @Override
  protected @Nullable Project getProject() {
    return myProject;
  }
}
