/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleSettingsManager implements PersistentStateComponent<CodeStyleSettingsManager> {
  private static final Logger LOG = Logger.getInstance(CodeStyleSettingsManager.class);

  public volatile CodeStyleSettings PER_PROJECT_SETTINGS = null;
  public volatile boolean USE_PER_PROJECT_SETTINGS = false;
  public volatile String PREFERRED_PROJECT_CODE_STYLE = null;

  private volatile CodeStyleSettings myTemporarySettings;
  private volatile boolean myIsLoaded = false;

  public static CodeStyleSettingsManager getInstance(@Nullable Project project) {
    if (project == null || project.isDefault()) return getInstance();
    ProjectCodeStyleSettingsManager projectSettingsManager = ServiceManager.getService(project, ProjectCodeStyleSettingsManager.class);
    if (!projectSettingsManager.isLoaded()) {
      synchronized (projectSettingsManager) {
        if (!projectSettingsManager.isLoaded()) {
          LegacyCodeStyleSettingsManager legacySettingsManager = ServiceManager.getService(project, LegacyCodeStyleSettingsManager.class);
          if (legacySettingsManager != null && legacySettingsManager.getState() != null) {
            try {
              //noinspection deprecation
              DefaultJDOMExternalizer.readExternal(projectSettingsManager, legacySettingsManager.getState());
            }
            catch (InvalidDataException e) {
              LOG.error(e);
            }
            LOG.info("Imported old project code style settings.");
          }
        }
      }
    }
    return projectSettingsManager;
  }

  public static CodeStyleSettingsManager getInstance() {
    return ServiceManager.getService(AppCodeStyleSettingsManager.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public CodeStyleSettingsManager(Project project) {
  }
  public CodeStyleSettingsManager() {}

  @NotNull
  public static CodeStyleSettings getSettings(@Nullable final Project project) {
    return getInstance(project).getCurrentSettings();
  }

  @NotNull
  public CodeStyleSettings getCurrentSettings() {
    CodeStyleSettings temporarySettings = myTemporarySettings;
    if (temporarySettings != null) return temporarySettings;
    CodeStyleSettings projectSettings = PER_PROJECT_SETTINGS;
    if (USE_PER_PROJECT_SETTINGS && projectSettings != null) return projectSettings;
    return CodeStyleSchemes.getInstance().findPreferredScheme(PREFERRED_PROJECT_CODE_STYLE).getCodeStyleSettings();
  }

  @Override
  public CodeStyleSettingsManager getState() {
    return this;
  }

  @Override
  public void loadState(CodeStyleSettingsManager state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public CodeStyleSettings getTemporarySettings() {
    return myTemporarySettings;
  }

  public void setTemporarySettings(CodeStyleSettings settings) {
    myTemporarySettings = settings;
  }

  public void dropTemporarySettings() {
    myTemporarySettings = null;
  }

  public boolean isLoaded() {
    return myIsLoaded;
  }
}
