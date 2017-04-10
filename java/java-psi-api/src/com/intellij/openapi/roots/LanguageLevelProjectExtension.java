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
package com.intellij.openapi.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class LanguageLevelProjectExtension {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.LanguageLevelProjectExtension");

  public static LanguageLevelProjectExtension getInstance(Project project) {
    return ServiceManager.getService(project, LanguageLevelProjectExtension.class);
  }

  @NotNull
  public abstract LanguageLevel getLanguageLevel();

  public abstract void setLanguageLevel(@NotNull LanguageLevel languageLevel);

  private Boolean myDefault;

  /**
   * Auto-detect language level from project JDK maximum possible level.
   * @return null if the property is not set yet (e.g. after migration).
   */
  @Nullable
  public Boolean getDefault() {
    return myDefault;
  }

  public void setDefault(@Nullable Boolean value) {
    myDefault = value;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.debug("Set " + value + "\n" + ExceptionUtil.currentStackTrace());
    }
  }

  public boolean isDefault() {
    return myDefault != null && myDefault;
  }

  public void languageLevelsChanged() {
  }
}
