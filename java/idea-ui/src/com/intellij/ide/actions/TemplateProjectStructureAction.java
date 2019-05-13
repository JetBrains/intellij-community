// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

public class TemplateProjectStructureAction extends ShowStructureSettingsAction {
  public TemplateProjectStructureAction() {
    String projectConceptName = StringUtil.capitalize(IdeUICustomization.getInstance().getProjectConceptName());
    getTemplatePresentation().setText(ActionsBundle.message("action.TemplateProjectStructure.text.template", projectConceptName));
    getTemplatePresentation().setDescription(ActionsBundle.message("action.TemplateProjectStructure.description.template", projectConceptName));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    showDialog(ProjectManager.getInstance().getDefaultProject());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getPlace().equals(ActionPlaces.WELCOME_SCREEN)) {
      e.getPresentation().setText(StringUtil.trimEnd(getTemplatePresentation().getText(), "..."));
    }
  }
}