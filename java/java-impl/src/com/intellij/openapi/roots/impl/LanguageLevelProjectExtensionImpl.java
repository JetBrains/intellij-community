// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.pom.java.JavaRelease;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import kotlin.Unit;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * @author anna
 */
public final class LanguageLevelProjectExtensionImpl extends LanguageLevelProjectExtension {
  private record LanguageLevelExtensionState(
    @Nullable LanguageLevel myLanguageLevel,
    @Nullable Boolean myDefault
  ) {
  }

  private static final String LANGUAGE_LEVEL = "languageLevel";
  private static final String DEFAULT_ATTRIBUTE = "default";
  private static final Logger LOG = Logger.getInstance(LanguageLevelProjectExtensionImpl.class);

  private final boolean useWsm = Registry.is("project.root.manager.over.wsm", false);
  private final Project myProject;
  private LanguageLevel myCurrentLevel;

  // This field is not used when useWsm set to `true`
  private @NotNull LanguageLevelExtensionState myLanguageLevelState = new LanguageLevelExtensionState(null, null);

  public LanguageLevelProjectExtensionImpl(final Project project) {
    myProject = project;
  }

  public static LanguageLevelProjectExtensionImpl getInstanceImpl(Project project) {
    return (LanguageLevelProjectExtensionImpl)getInstance(project);
  }

  /**
   * Returns true if the state was changed after read
   */
  private boolean readExternal(final Element element) {
    Logger.getInstance(LanguageLevelProjectExtensionImpl.class).assertTrue(!useWsm, "Should read state from WSM");
    String level = element.getAttributeValue(LANGUAGE_LEVEL);
    LanguageLevel languageLevelOldValue = myLanguageLevelState.myLanguageLevel;
    LanguageLevel languageLevelNewValue = readLanguageLevel(level);
    String defaultNewValueStr = element.getAttributeValue(DEFAULT_ATTRIBUTE);
    Boolean defaultOldValue = myLanguageLevelState.myDefault;
    Boolean defaultNewValue = defaultNewValueStr != null ? Boolean.parseBoolean(defaultNewValueStr) : null;
    myLanguageLevelState = new LanguageLevelExtensionState(languageLevelNewValue, defaultNewValue);
    return !Objects.equals(defaultOldValue, defaultNewValue) || languageLevelOldValue != languageLevelNewValue;
  }

  private static @Nullable LanguageLevel readLanguageLevel(@Nullable String level) {
    if (level != null) {
      for (LanguageLevel languageLevel : LanguageLevel.getEntries()) {
        if (level.equals(languageLevel.name())) {
          return languageLevel;
        }
      }
      return JavaRelease.getHighest();
    }
    else {
      return null;
    }
  }

  private void writeExternal(final Element element) {
    Logger.getInstance(LanguageLevelProjectExtensionImpl.class).assertTrue(!useWsm, "Should write state to WSM");
    if (myLanguageLevelState.myLanguageLevel != null) {
      element.setAttribute(LANGUAGE_LEVEL, myLanguageLevelState.myLanguageLevel.name());
    }

    Boolean aBoolean = myLanguageLevelState.myDefault;
    if (aBoolean != null && aBoolean != myProject.isDefault()) { // do not write default 'true' for default project
      element.setAttribute(DEFAULT_ATTRIBUTE, Boolean.toString(aBoolean));
    }
  }

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    return getLanguageLevelOrDefault();
  }

  private @NotNull LanguageLevel getLanguageLevelOrDefault() {
    LanguageLevelExtensionState ll = getLanguageLevelInternal();
    return ObjectUtils.chooseNotNull(ll.myLanguageLevel, JavaRelease.getHighest());
  }

  @Override
  @RequiresWriteLock(generateAssertion = false)
  public void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    LOG.assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed(),
                   "Language level may only be updated under write action. " +
                   "Please acquire write action before invoking setLanguageLevel.");

    // we don't use here getLanguageLevelOrDefault() - if null, just set to provided value because our default (JavaRelease.getHighest())
    // is changed every java release
    LanguageLevelExtensionState currentLevel = getLanguageLevelInternal();
    if (currentLevel.myLanguageLevel != languageLevel) {
      setLanguageLevelInternal(languageLevel, false);
      languageLevelsChanged();
    }
  }

  @RequiresWriteLock(generateAssertion = false)
  private void setLanguageLevelInternal(@Nullable LanguageLevel languageLevel, @Nullable Boolean isDefault) {
    if (useWsm) {
      ThreadingAssertions.assertWriteAccess();

      WorkspaceModel workspaceModel = WorkspaceModel.getInstance(myProject);
      workspaceModel.updateProjectModel("setLanguageLevelInternal", mutableStorage -> {
        JavaEntitiesWsmUtils.addOrModifyJavaProjectSettingsEntity(myProject, mutableStorage, entity -> {
          var ll = languageLevel != null ? languageLevel.name() : null;
          entity.setLanguageLevelId(ll);
          entity.setLanguageLevelDefault(isDefault);
        });
        return Unit.INSTANCE;
      });
    }
    else {
      myLanguageLevelState = new LanguageLevelExtensionState(languageLevel, isDefault);
    }
  }

  private @NotNull LanguageLevelExtensionState getLanguageLevelInternal() {
    if (useWsm) {
      JavaProjectSettingsEntity entity = JavaEntitiesWsmUtils.getSingleEntity(WorkspaceModel.getInstance(myProject).getCurrentSnapshot(), JavaProjectSettingsEntity.class);

      if (entity != null) {
        LanguageLevel llParsed = readLanguageLevel(entity.getLanguageLevelId());
        return new LanguageLevelExtensionState(llParsed, entity.getLanguageLevelDefault());
      }
      else {
        return new LanguageLevelExtensionState(null, null);
      }
    }
    else {
      return myLanguageLevelState;
    }
  }


  @Override
  public @Nullable Boolean getDefault() {
    return getLanguageLevelInternal().myDefault;
  }

  @Override
  @RequiresWriteLock(generateAssertion = false)
  public void setDefault(@Nullable Boolean newDefault) {
    LOG.assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed(),
                   "Language level may only be updated under write action. " +
                   "Please acquire write action before invoking setDefault.");
    LanguageLevelExtensionState current = getLanguageLevelInternal();
    if (current.myDefault != newDefault) {
      setLanguageLevelInternal(current.myLanguageLevel, newDefault);
    }
  }

  @Override
  public void languageLevelsChanged() {
    languageLevelsChanged(myProject);
  }

  public static void languageLevelsChanged(@NotNull Project project) {
    if (!project.isDefault()) {
      project.getMessageBus().syncPublisher(LANGUAGE_LEVEL_CHANGED_TOPIC).onLanguageLevelsChanged();
      ProjectRootManager.getInstance(project).incModificationCount();
      JavaLanguageLevelPusher.pushLanguageLevel(project);
    }
  }

  private void projectSdkChanged(@Nullable Sdk sdk) {
    if (isDefault() && sdk != null) {
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
      if (version != null) {
        setLanguageLevel(version.getMaxLanguageLevel());
        setDefault(true);
      }
    }
  }

  public void setCurrentLevel(LanguageLevel level) {
    myCurrentLevel = level;
  }

  public LanguageLevel getCurrentLevel() {
    return myCurrentLevel;
  }

  @TestOnly
  public void resetDefaults() {
    setLanguageLevelInternal(null, null);
  }

  static final class MyProjectExtension extends ProjectExtension {
    private final LanguageLevelProjectExtensionImpl myInstance;

    MyProjectExtension(@NotNull Project project) {
      myInstance = ((LanguageLevelProjectExtensionImpl)getInstance(project));
    }

    @Override
    public boolean readExternalElement(@NotNull Element element) {
      return myInstance.readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
      myInstance.writeExternal(element);
    }

    @Override
    public void projectSdkChanged(@Nullable Sdk sdk) {
      myInstance.projectSdkChanged(sdk);
    }
  }
}