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
package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.ui.wizard.WizardDialog;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class StartupWizard extends WizardDialog<StartupWizardModel> {
  public StartupWizard(final List<ApplicationInfoEx.PluginChooserPage> pluginChooserPages) {
    super(true, true, new StartupWizardModel(pluginChooserPages));
    getPeer().setAppIcons();
  }

  public StartupWizard(final Project project, final List<ApplicationInfoEx.PluginChooserPage> pluginChooserPages) {
    super(project, true, new StartupWizardModel(pluginChooserPages));
    getPeer().setAppIcons();
  }

  @Override
  public void onWizardGoalAchieved() {
    super.onWizardGoalAchieved();

    try {
      PluginManager.saveDisabledPlugins(myModel.getDisabledPluginIds(), false);
    }
    catch (IOException e) {
      // ignore?
    }
  }

  public static void run() {
    new StartupWizard(ApplicationInfoImpl.getShadowInstance().getPluginChooserPages()).show();
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        StartupWizard.run();
      }
    });
  }

  @Override
  protected Dimension getWindowPreferredSize() {
    return new Dimension(600, 350);
  }

  public void setCancelText(String text) {
    myModel.getCurrentNavigationState().CANCEL.setName(text);
  }
}
