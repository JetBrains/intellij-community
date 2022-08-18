// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

public class TemplateProjectPropertiesAction extends AnAction implements DumbAware {
  public TemplateProjectPropertiesAction() {
    getTemplatePresentation().setText(() -> IdeUICustomization.getInstance().projectMessage("action.TemplateProjectProperties.text.template", CommonBundle.settingsTitle()));
    getTemplatePresentation().setDescription(() -> IdeUICustomization.getInstance().projectMessage("action.TemplateProjectProperties.description.template"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    ShowSettingsUtil.getInstance().showSettingsDialog(defaultProject, ShowSettingsUtilImpl.getConfigurableGroups(defaultProject, false));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
