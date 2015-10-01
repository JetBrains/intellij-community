/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.application.options.pathMacros;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author dsl
 */
public class PathMacroConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NonNls
  public static final String HELP_ID = "preferences.pathVariables";
  private PathMacroListEditor myEditor;

  @Override
  public JComponent createComponent() {
    myEditor = new PathMacroListEditor();
    return myEditor.getPanel();
  }

  @Override
  public void apply() throws ConfigurationException {
    myEditor.commit();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      StorageUtil.checkUnknownMacros(project, false);
    }
  }

  @Override
  public void reset() {
    myEditor.reset();
  }

  @Override
  public void disposeUIResources() {
    myEditor = null;
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.path.variables");
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return HELP_ID;
  }

  @Override
  public boolean isModified() {
    return myEditor != null && myEditor.isModified();
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
