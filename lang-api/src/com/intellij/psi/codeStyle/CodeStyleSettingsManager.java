/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class CodeStyleSettingsManager implements ApplicationComponent, ProjectComponent, JDOMExternalizable {
  public CodeStyleSettings PER_PROJECT_SETTINGS = null;
  public boolean USE_PER_PROJECT_SETTINGS = false;
  private CodeStyleSettings myTemporarySettings;

  public static CodeStyleSettingsManager getInstance(Project project) {
    return project.getComponent(CodeStyleSettingsManager.class);
  }

  public static CodeStyleSettingsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CodeStyleSettingsManager.class);
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
    DefaultJDOMExternalizer.writeExternal(this, element);
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
}
