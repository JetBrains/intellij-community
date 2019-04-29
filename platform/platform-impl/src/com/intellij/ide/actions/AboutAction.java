// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AboutAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!ActionPlaces.isMacSystemMenuAction(e));
    e.getPresentation().setDescription("Show information about " + ApplicationNamesInfo.getInstance().getFullProductName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform(e.getData(CommonDataKeys.PROJECT));
  }

  public static void perform(@Nullable Project project) {
    AboutPopup.show(WindowManager.getInstance().suggestParentWindow(project), false);
  }
}
