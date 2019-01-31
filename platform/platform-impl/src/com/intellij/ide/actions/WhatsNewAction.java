// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class WhatsNewAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    BrowserUtil.browse(ApplicationInfoEx.getInstanceEx().getWhatsNewUrl());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean visible = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl() != null;
    e.getPresentation().setVisible(visible);
    if (visible) {
      e.getPresentation()
        .setText(IdeBundle.message("whatsnew.action.custom.text", ApplicationNamesInfo.getInstance().getFullProductName()));
      e.getPresentation().setDescription(
        IdeBundle.message("whatsnew.action.custom.description", ApplicationNamesInfo.getInstance().getFullProductName()));
    }
  }
}
