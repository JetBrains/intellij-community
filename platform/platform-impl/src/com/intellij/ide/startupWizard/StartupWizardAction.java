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

import com.intellij.ide.customize.CustomizeIDEWizardDialog;
import com.intellij.ide.customize.CustomizeIdeaWizardStepsProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class StartupWizardAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = ApplicationManager.getApplication().isInternal() ||
                      !ApplicationInfoEx.getInstanceEx().getPluginChooserPages().isEmpty();
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (ApplicationManager.getApplication().isInternal()) {
      new CustomizeIDEWizardDialog(new CustomizeIdeaWizardStepsProvider()).show();
      return;
    }

    List<ApplicationInfoEx.PluginChooserPage> pages = ApplicationInfoEx.getInstanceEx().getPluginChooserPages();
    if (!pages.isEmpty()) {
      StartupWizard startupWizard = new StartupWizard(e.getProject(), pages);
      String title = ApplicationNamesInfo.getInstance().getFullProductName() + " Plugin Configuration Wizard";
      startupWizard.setTitle(title);
      startupWizard.show();
      if (startupWizard.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        Messages.showInfoMessage(e.getProject(), "Please restart the IDE to apply changes", title);
      }
    }
  }
}