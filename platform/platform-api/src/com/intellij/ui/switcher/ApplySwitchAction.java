/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public class ApplySwitchAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    Project project = getProject(e);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    SwitchManager mgr = SwitchManager.getInstance(project);

    boolean switchActionActive = mgr != null && mgr.isSessionActive();
    e.getPresentation().setEnabled(switchActionActive);
    if (!switchActionActive) {
      QuickActionProvider quickActionProvider = QuickActionProvider.KEY.getData(e.getDataContext());
      e.getPresentation().setEnabled(quickActionProvider != null);
    }
  }

  private Project getProject(AnActionEvent e) {
    return PlatformDataKeys.PROJECT.getData(e.getDataContext());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = getProject(e);

    SwitchManager switchManager = SwitchManager.getInstance(project);
    if (switchManager.canApplySwitch()) {
      switchManager.applySwitch();
    } else {
      switchManager.resetSession();
      QuickActionManager.getInstance(project).showQuickActions();
    }
    
  }
}
