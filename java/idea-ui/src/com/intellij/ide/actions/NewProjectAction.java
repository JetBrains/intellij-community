// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import org.jetbrains.annotations.NotNull;

public class NewProjectAction extends AnAction implements DumbAware {
  @Override
  public boolean startInTransaction() {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    NewProjectWizard wizard = new NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
    Project eventProject = getEventProject(e);
    ApplicationManager.getApplication().invokeLater(() -> {
      NewProjectUtil.createNewProject(eventProject, wizard);
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Welcome.CreateNewProject);
    }
  }
}
