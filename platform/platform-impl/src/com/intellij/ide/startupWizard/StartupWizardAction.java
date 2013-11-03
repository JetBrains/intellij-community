/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

/**
 * @author yole
 */
public class StartupWizardAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final StartupWizard startupWizard = new StartupWizard(project, ApplicationInfoImpl.getShadowInstance().getPluginChooserPages());
    final String title = ApplicationNamesInfo.getInstance().getFullProductName() + " Plugin Configuration Wizard";
    startupWizard.setTitle(title);
    startupWizard.show();
    if (startupWizard.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      Messages.showInfoMessage(project, "To apply the changes, please restart " + ApplicationNamesInfo.getInstance().getFullProductName(),
                               title);
    }
  }
}
