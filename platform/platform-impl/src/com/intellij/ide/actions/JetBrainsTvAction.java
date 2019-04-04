// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class JetBrainsTvAction extends AnAction implements DumbAware {
  private final String myUrl;

  public JetBrainsTvAction() {
    myUrl = ApplicationInfo.getInstance().getJetbrainsTvUrl();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = myUrl != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myUrl != null) {
      BrowserUtil.browse(myUrl);
    }
  }
}