// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

public class TemplateProjectPropertiesAction extends AnAction implements DumbAware {
  public TemplateProjectPropertiesAction() {
    String projectConceptName = StringUtil.capitalize(IdeUICustomization.getInstance().getProjectConceptName());
    getTemplatePresentation().setText(ActionsBundle.message("action.TemplateProjectProperties.text.template", CommonBundle.settingsTitle(), projectConceptName));
    getTemplatePresentation().setDescription(ActionsBundle.message("action.TemplateProjectProperties.description.template", projectConceptName));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    ShowSettingsUtil.getInstance().showSettingsDialog(defaultProject, ShowSettingsUtilImpl.getConfigurableGroups(defaultProject, false));
  }
}
