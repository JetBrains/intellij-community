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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class JdksConfigurable extends NamedConfigurable<ProjectJdksModel> {
  private final ProjectJdksModel myJdkTableConfigurable;
  public static final String JDKS = ProjectBundle.message("jdks.node.display.name");
  public static final Icon ICON = IconLoader.getIcon("/modules/jdks.png");


  public JdksConfigurable(final ProjectJdksModel jdksTreeModel) {
    myJdkTableConfigurable = jdksTreeModel;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public ProjectJdksModel getEditableObject() {
    return myJdkTableConfigurable;
  }

  public String getBannerSlogan() {
    return JDKS;
  }

  public String getDisplayName() {
    return JDKS;
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {     //todo help
    return "preferences.jdks";
  }


  public JComponent createOptionsPanel() {
    return new PanelWithText(ProjectBundle.message("project.roots.jdks.node.text"));
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {

  }
}
