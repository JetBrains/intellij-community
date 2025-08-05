// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.JavaRelease;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * @author anna
 */
public final class LanguageLevelProjectExtensionImpl extends LanguageLevelProjectExtension {
  private static final String LANGUAGE_LEVEL = "languageLevel";
  private static final String DEFAULT_ATTRIBUTE = "default";
  private static final Logger LOG = Logger.getInstance(LanguageLevelProjectExtensionImpl.class);

  private final Project myProject;
  private @Nullable LanguageLevel myLanguageLevel;
  private @Nullable Boolean myDefault;
  private LanguageLevel myCurrentLevel;

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
    String level = element.getAttributeValue(LANGUAGE_LEVEL);
    LanguageLevel languageLevelOldValue = myLanguageLevel;
    if (level == null) {
      myLanguageLevel = null;
    }
    else {
      myLanguageLevel = readLanguageLevel(level);
    }
    String aDefault = element.getAttributeValue(DEFAULT_ATTRIBUTE);
    Boolean defaultOldValue = getDefault();
    if (aDefault != null) {
      myDefault = Boolean.parseBoolean(aDefault);
    }
    return !Objects.equals(defaultOldValue, getDefault()) || languageLevelOldValue != myLanguageLevel;
  }

  private static LanguageLevel readLanguageLevel(String level) {
    for (LanguageLevel languageLevel : LanguageLevel.getEntries()) {
      if (level.equals(languageLevel.name())) {
        return languageLevel;
      }
    }
    return JavaRelease.getHighest();
  }

  private void writeExternal(final Element element) {
    if (myLanguageLevel != null) {
      element.setAttribute(LANGUAGE_LEVEL, myLanguageLevel.name());
    }

    Boolean aBoolean = getDefault();
    if (aBoolean != null && aBoolean != myProject.isDefault()) { // do not write default 'true' for default project
      element.setAttribute(DEFAULT_ATTRIBUTE, Boolean.toString(aBoolean));
    }
  }

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    return getLanguageLevelOrDefault();
  }

  private @NotNull LanguageLevel getLanguageLevelOrDefault() {
    return ObjectUtils.chooseNotNull(myLanguageLevel, JavaRelease.getHighest());
  }

  @Override
  @RequiresWriteLock(generateAssertion = false)
  public void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    LOG.assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed(),
                   "Language level may only be updated under write action. " +
                   "Please acquire write action before invoking setLanguageLevel.");

    // we don't use here getLanguageLevelOrDefault() - if null, just set to provided value because our default (JavaRelease.getHighest())
    // is changed every java release
    if (myLanguageLevel != languageLevel) {
      myLanguageLevel = languageLevel;
      setDefault(false);
      languageLevelsChanged();
    }
  }

  @Override
  public @Nullable Boolean getDefault() {
    return myDefault;
  }

  @Override
  @RequiresWriteLock(generateAssertion = false)
  public void setDefault(@Nullable Boolean newDefault) {
    LOG.assertTrue(ApplicationManager.getApplication().isWriteAccessAllowed(),
                   "Language level may only be updated under write action. " +
                   "Please acquire write action before invoking setDefault.");

    myDefault = newDefault;
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
    myLanguageLevel = null;
    setDefault(null);
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