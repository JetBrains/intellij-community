// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import org.jetbrains.annotations.NotNull;

public class NewProjectAction extends AnAction implements DumbAware, NewProjectOrModuleAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    NewProjectWizard wizard = new NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
    NewProjectUtil.createNewProject(wizard);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Welcome.CreateNewProject);
      e.getPresentation().setSelectedIcon(AllIcons.Welcome.CreateNewProjectSelected);
    }
    updateActionText(this, e);
  }

  @NotNull
  @Override
  public String getActionText(boolean isInNewSubmenu, boolean isInJavaIde) {
    return JavaUiBundle.message("new.project.action.text", isInNewSubmenu ? 1 : 0, isInJavaIde ? 1 : 0);
  }

  public static <T extends AnAction & NewProjectOrModuleAction> void updateActionText(@NotNull T action, @NotNull AnActionEvent e) {
    String actionText;
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      actionText = action.getTemplateText();
    }
    else {
      boolean inJavaIde = IdeLanguageCustomization.getInstance().getPrimaryIdeLanguages().contains(JavaLanguage.INSTANCE);
      boolean fromNewSubMenu = isInvokedFromNewSubMenu(action, e);
      actionText = action.getActionText(fromNewSubMenu, inJavaIde);
    }
    e.getPresentation().setText(actionText);
  }

  private static boolean isInvokedFromNewSubMenu(@NotNull AnAction action, @NotNull AnActionEvent e) {
    return NewActionGroup.isActionInNewPopupMenu(action) &&
           (ActionPlaces.MAIN_MENU.equals(e.getPlace()) || ActionPlaces.isPopupPlace(e.getPlace()));
  }
}
