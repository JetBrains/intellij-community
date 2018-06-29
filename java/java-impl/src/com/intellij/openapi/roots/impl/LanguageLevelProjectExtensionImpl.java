/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 * @since 26-Dec-2007
 */
public class LanguageLevelProjectExtensionImpl extends LanguageLevelProjectExtension {
  private static final String LANGUAGE_LEVEL = "languageLevel";
  private static final String DEFAULT_ATTRIBUTE = "default";

  private final Project myProject;
  private LanguageLevel myLanguageLevel;
  private LanguageLevel myCurrentLevel;

  public LanguageLevelProjectExtensionImpl(final Project project) {
    myProject = project;
    setDefault(project.isDefault() ? true : null);
  }

  public static LanguageLevelProjectExtensionImpl getInstanceImpl(Project project) {
    return (LanguageLevelProjectExtensionImpl)getInstance(project);
  }

  private void readExternal(final Element element) {
    String level = element.getAttributeValue(LANGUAGE_LEVEL);
    if (level == null) {
      myLanguageLevel = null;
    }
    else {
      myLanguageLevel = readLanguageLevel(level);
    }
    String aDefault = element.getAttributeValue(DEFAULT_ATTRIBUTE);
    setDefault(aDefault == null ? null : Boolean.parseBoolean(aDefault));
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

    if (!myProject.isDefault()) {
      Boolean aBoolean = getDefault();
      if (aBoolean != null) {
        element.setAttribute(DEFAULT_ATTRIBUTE, Boolean.toString(aBoolean));
      }
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
      languageLevelsChanged();
    }
  }

  @Override
  public void languageLevelsChanged() {
    if (!myProject.isDefault()) {
      ProjectRootManager.getInstance(myProject).incModificationCount();
      JavaLanguageLevelPusher.pushLanguageLevel(myProject);
    }
  }

  private void projectSdkChanged(@Nullable Sdk sdk) {
    if (isDefault() && sdk != null) {
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
      if (version != null) {
        setLanguageLevel(version.getMaxLanguageLevel());
      }
    }
  }

  public void setCurrentLevel(LanguageLevel level) {
    myCurrentLevel = level;
  }

  public LanguageLevel getCurrentLevel() {
    return myCurrentLevel;
  }

  public static class MyProjectExtension extends ProjectExtension {
    private final LanguageLevelProjectExtensionImpl myInstance;

    public MyProjectExtension(final Project project) {
      myInstance = ((LanguageLevelProjectExtensionImpl)getInstance(project));
    }

    @Override
    public void readExternal(@NotNull Element element) {
      myInstance.readExternal(element);
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