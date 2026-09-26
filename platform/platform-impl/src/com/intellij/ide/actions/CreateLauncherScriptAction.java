// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.help.impl.HelpManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CreateLauncherScriptAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
  private static final String TOPIC = "Working_with_the_IDE_Features_from_Command_Line";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    showInstructions(event.getProject());
  }

  private static void showInstructions(@Nullable Project project) {
    String message;
    if (ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX) {
      message = ApplicationBundle.message("cli.launcher.message.toolbox");
    }
    else if (ExternalUpdateManager.ACTUAL == ExternalUpdateManager.SNAP) {
      var name = ApplicationNamesInfo.getInstance().getScriptName();
      message = ApplicationBundle.message("cli.launcher.message.snap", name);
    }
    else if (OS.CURRENT == OS.Windows) {
      var dir = PathManager.getBinDir();
      var name = ApplicationNamesInfo.getInstance().getScriptName() + (Boolean.getBoolean("ide.native.launcher") ? ".exe" : ".bat");
      var url = HelpManagerImpl.getHelpUrl(TOPIC);
      message = ApplicationBundle.message("cli.launcher.message.windows", dir, name, url);
    }
    else if (OS.CURRENT == OS.macOS) {
      var dir = PathManager.getHomeDir().resolve("MacOS");
      var name = ApplicationNamesInfo.getInstance().getScriptName();
      var url = HelpManagerImpl.getHelpUrl(TOPIC);
      message = ApplicationBundle.message("cli.launcher.message.unix", dir, name, url);
    }
    else {
      var dir = PathManager.getBinDir();
      var name = ApplicationNamesInfo.getInstance().getScriptName() + (Boolean.getBoolean("ide.native.launcher") ? "" : ".sh");
      var url = HelpManagerImpl.getHelpUrl(TOPIC);
      message = ApplicationBundle.message("cli.launcher.message.unix", dir, name, url);
    }
    Messages.showInfoMessage(project, message, ApplicationBundle.message("cli.launcher.message.title"));
  }
}
