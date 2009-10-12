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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 17-Aug-2006
 * Time: 14:10:54
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ModulesConfigurable extends NamedConfigurable<ModuleManager> {
  private static final Icon PROJECT_ICON = IconLoader.getIcon("/modules/modulesNode.png");

  private final ModuleManager myManager;

  public ModulesConfigurable(ModuleManager manager) {
    myManager = manager;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public ModuleManager getEditableObject() {
    return myManager;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.modules.display.name");
  }

  public JComponent createOptionsPanel() {
    return new PanelWithText(ProjectBundle.message("project.roots.modules.description"));
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.modules.display.name");
  }

  public Icon getIcon() {
    return PROJECT_ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.structureModulesPage";
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
    //do nothing
  }

  public void reset() {
    //do nothing
  }

  public void disposeUIResources() {
    //do nothing
  }
}
