// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class TemplateProjectStructureAction extends ShowStructureSettingsAction {
  public TemplateProjectStructureAction() {
    getTemplatePresentation().setText(JavaUiBundle.messagePointer("action.TemplateProjectStructure.text"));
    getTemplatePresentation().setDescription(JavaUiBundle.messagePointer("action.TemplateProjectStructure.description"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    showDialog(ProjectManager.getInstance().getDefaultProject());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getPlace().equals(ActionPlaces.WELCOME_SCREEN)) {
      final String text = getTemplatePresentation().getText();
      if (text == null) return;
      e.getPresentation().setText(StringUtil.trimEnd(text, "..."));
    }
  }
}