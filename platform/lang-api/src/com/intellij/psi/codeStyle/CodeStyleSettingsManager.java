/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class CodeStyleSettingsManager implements PersistentStateComponent<Element> {

  private static final Logger LOG = Logger.getInstance("#" + CodeStyleSettingsManager.class.getName());

  public CodeStyleSettings PER_PROJECT_SETTINGS = null;
  public boolean USE_PER_PROJECT_SETTINGS = false;
  private CodeStyleSettings myTemporarySettings;
  private boolean myIsLoaded = false;

  public static CodeStyleSettingsManager getInstance(Project project) {
    ProjectCodeStyleSettingsManager projectSettingsManager = ServiceManager.getService(project, ProjectCodeStyleSettingsManager.class);
    if (!projectSettingsManager.isLoaded()) {
      LegacyCodeStyleSettingsManager legacySettingsManager = ServiceManager.getService(project, LegacyCodeStyleSettingsManager.class);
      if (legacySettingsManager != null && legacySettingsManager.getState() != null) {
        projectSettingsManager.loadState(legacySettingsManager.getState());
        LOG.info("Imported old project code style settings.");
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

  public static CodeStyleSettings getSettings(Project project) {
    final CodeStyleSettingsManager instance = project == null ? getInstance() : getInstance(project);
    return instance.getCurrentSettings();
  }

  public CodeStyleSettings getCurrentSettings() {
    if (myTemporarySettings != null) return myTemporarySettings;
    if (USE_PER_PROJECT_SETTINGS && PER_PROJECT_SETTINGS != null) return PER_PROJECT_SETTINGS;
    return CodeStyleSchemes.getInstance().getCurrentScheme().getCodeStyleSettings();
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element, new DifferenceFilter<CodeStyleSettingsManager>(this, new CodeStyleSettingsManager()));
  }

  public Element getState() {
    Element result = new Element("state");
    try {
      writeExternal(result);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return result;
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
      myIsLoaded = true;
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void disposeComponent() {}
  public void initComponent() {}
  public void projectOpened() {}
  public void projectClosed() {}

  @NotNull
  public String getComponentName() {
    return "CodeStyleSettingsManager";
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
