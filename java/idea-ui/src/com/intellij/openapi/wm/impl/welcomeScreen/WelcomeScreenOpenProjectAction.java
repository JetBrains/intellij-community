package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.actions.OpenProjectAction;
import com.intellij.ui.UIBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;

/**
 * @author yole
 */
public class WelcomeScreenOpenProjectAction extends OpenProjectAction {
  public WelcomeScreenOpenProjectAction() {
    getTemplatePresentation().setDescription(UIBundle.message("welcome.screen.open.project.action.description",
                                                              ApplicationNamesInfo.getInstance().getFullProductName()));
  }
}