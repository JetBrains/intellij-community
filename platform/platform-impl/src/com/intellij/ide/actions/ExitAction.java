// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;

public class ExitAction extends AnAction implements DumbAware {
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!SystemInfo.isMacSystemMenu || !ActionPlaces.MAIN_MENU.equals(e.getPlace()));
  }

  public void actionPerformed(AnActionEvent e) {
    ApplicationManager.getApplication().exit();
  }
}