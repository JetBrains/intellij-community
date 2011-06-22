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
 * Date: 16-Jul-2006
 * Time: 16:52:27
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ModuleGroupConfigurable extends NamedConfigurable<ModuleGroup> {
  private final ModuleGroup myModuleGroup;

  public ModuleGroupConfigurable(final ModuleGroup moduleGroup) {
    myModuleGroup = moduleGroup;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public ModuleGroup getEditableObject() {
    return myModuleGroup;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("module.group.banner.text", myModuleGroup.toString());
  }

  public String getDisplayName() {
    return myModuleGroup.toString();
  }

  public Icon getIcon() {
    return PlatformIcons.OPENED_MODULE_GROUP_ICON;
  }

  public Icon getIcon(final boolean open) {
    return open ? PlatformIcons.OPENED_MODULE_GROUP_ICON : PlatformIcons.CLOSED_MODULE_GROUP_ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }


  public JComponent createOptionsPanel() {
    return new PanelWithText(ProjectBundle.message("project.roots.module.groups.text"));
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
