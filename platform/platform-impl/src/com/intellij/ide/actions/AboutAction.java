// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AboutAction extends AnAction implements DumbAware, LightEditCompatible {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!ActionPlaces.isMacSystemMenuAction(e));
    e.getPresentation().setDescription(ActionsBundle.message("action.About.description.specialized", ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform(e.getData(CommonDataKeys.PROJECT));
  }

  public static void perform(@Nullable Project project) {
    if (Registry.is("ide.new.about.dialog")) {
      new AboutDialog(project).show();
    }
    else {
      AboutPopup.show(WindowManager.getInstance().suggestParentWindow(project), false);
    }
  }
}
