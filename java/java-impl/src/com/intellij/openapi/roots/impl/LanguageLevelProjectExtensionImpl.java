// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
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

  private final Project myProject;
  private LanguageLevel myLanguageLevel;
  private LanguageLevel myCurrentLevel;

  public LanguageLevelProjectExtensionImpl(final Project project) {
    myProject = project;
    setDefault(project.isDefault() ? true : null);
    project.getMessageBus().connect().subscribe(WorkspaceModelTopics.CHANGED,
      new WorkspaceModelChangeListener() {
        @Override
        public void changed(@NotNull VersionedStorageChange event) {
          if (event.getChanges(JavaModuleSettingsEntity.class).stream().anyMatch(change ->
            change instanceof EntityChange.Replaced<?> &&
            !Objects.equals(((EntityChange.Replaced<JavaModuleSettingsEntity>)change).getOldEntity().getLanguageLevelId(),
                            ((EntityChange.Replaced<JavaModuleSettingsEntity>)change).getNewEntity().getLanguageLevelId())
          )) {
            languageLevelsChanged();
          }
        }
      }
    );
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
      setDefault(Boolean.parseBoolean(aDefault));
    }
    return !Objects.equals(defaultOldValue, getDefault()) || languageLevelOldValue != myLanguageLevel;
  }

  private static LanguageLevel readLanguageLevel(String level) {
    for (LanguageLevel languageLevel : LanguageLevel.values()) {
      if (level.equals(languageLevel.name())) {
        return languageLevel;
      }
    }
    return LanguageLevel.HIGHEST;
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
  @NotNull
  public LanguageLevel getLanguageLevel() {
    return getLanguageLevelOrDefault();
  }

  @NotNull
  private LanguageLevel getLanguageLevelOrDefault() {
    return ObjectUtils.chooseNotNull(myLanguageLevel, LanguageLevel.HIGHEST);
  }

  @Override
  public void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    // we don't use here getLanguageLevelOrDefault() - if null, just set to provided value, because our default (LanguageLevel.HIGHEST) is changed every java release
    if (myLanguageLevel != languageLevel) {
      myLanguageLevel = languageLevel;
      setDefault(false);
      languageLevelsChanged();
    }
  }

  @Override
  public void languageLevelsChanged() {
    if (!myProject.isDefault()) {
      myProject.getMessageBus().syncPublisher(LANGUAGE_LEVEL_CHANGED_TOPIC).onLanguageLevelsChanged();
      ProjectRootManager.getInstance(myProject).incModificationCount();
      JavaLanguageLevelPusher.pushLanguageLevel(myProject);
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