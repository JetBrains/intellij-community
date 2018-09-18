// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class HelpTopicsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    HelpManager.getInstance().invokeHelp("top");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    boolean enabled = ApplicationInfo.helpAvailable();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }
}
