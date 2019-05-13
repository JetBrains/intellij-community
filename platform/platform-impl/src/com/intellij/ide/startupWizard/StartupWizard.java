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
package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.wizard.WizardDialog;
import com.intellij.util.ui.JBUI;

import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class StartupWizard extends WizardDialog<StartupWizardModel> {
  public StartupWizard(List<ApplicationInfoEx.PluginChooserPage> pluginChooserPages) {
    super(true, true, new StartupWizardModel(pluginChooserPages));
    getPeer().setAppIcons();
    myModel.getCurrentNavigationState().CANCEL.setName("Skip");
  }

  public StartupWizard(Project project, List<ApplicationInfoEx.PluginChooserPage> pluginChooserPages) {
    super(project, true, new StartupWizardModel(pluginChooserPages));
    getPeer().setAppIcons();
  }

  @Override
  public void onWizardGoalAchieved() {
    super.onWizardGoalAchieved();

    try {
      PluginManagerCore.saveDisabledPlugins(myModel.getDisabledPluginIds(), false);
    }
    catch (IOException e) {
      // ignore?
    }
  }

  @Override
  protected Dimension getWindowPreferredSize() {
    return JBUI.size(600, 350);
  }
}