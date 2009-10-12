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
 * Time: 14:13:02
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GlobalResourcesConfigurable extends NamedConfigurable {
  public static final Icon ICON = IconLoader.getIcon("/modules/globalResources.png");

  public void setDisplayName(final String name) {
    //do nothing
  }

  @Nullable
  public Object getEditableObject() {
    return null;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.global.resources.display.name");
  }

  public JComponent createOptionsPanel() {
    return new PanelWithText(ProjectBundle.message("project.roots.global.resources.description"));
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.global.resources.display.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "preferences.jdkGlobalLibs";
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