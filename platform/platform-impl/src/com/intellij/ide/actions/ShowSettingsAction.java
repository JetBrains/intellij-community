// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.actions.ShowSettingsUtilImplKt.scheduleDoShowSettingsDialogWithACheckThatProjectIsInitialized;

public final class ShowSettingsAction extends AnAction implements DumbAware, LightEditCompatible, ActionRemoteBehaviorSpecification.Frontend {
  public ShowSettingsAction() {
    super(CommonBundle.settingsAction(), CommonBundle.settingsActionDescription(), AllIcons.General.Settings);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!ActionPlaces.isMacSystemMenuAction(e));
    if (e.getPlace().equals(ActionPlaces.WELCOME_SCREEN)) {
      e.getPresentation().setText(CommonBundle.settingsTitle());
    }
    else if (SystemInfo.isMacOSVentura) {
      e.getPresentation().setText(CommonBundle.settingsAction());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    perform(project != null ? project : ProjectManager.getInstance().getDefaultProject());
  }

  public static void perform(@NotNull Project project) {
    scheduleDoShowSettingsDialogWithACheckThatProjectIsInitialized(project);
  }
}
