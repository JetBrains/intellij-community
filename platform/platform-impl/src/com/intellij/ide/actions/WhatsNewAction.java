// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class WhatsNewAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String text = e.getPresentation().getText();
    if (e.getProject() == null || text == null) {
      BrowserUtil.browse(ApplicationInfoEx.getInstanceEx().getWhatsNewUrl());
    } else {
      HTMLEditorProvider.Companion.openEditor(e.getProject(), text, ApplicationInfoEx.getInstanceEx().getWhatsNewUrl() + getEmbeddedSuffix(), null);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean visible = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl() != null;
    e.getPresentation().setVisible(visible);
    if (visible) {
      e.getPresentation()
        .setText(IdeBundle.messagePointer("whatsnew.action.custom.text", ApplicationNamesInfo.getInstance().getFullProductName()));
      e.getPresentation().setDescription(
        IdeBundle.messagePointer("whatsnew.action.custom.description", ApplicationNamesInfo.getInstance().getFullProductName()));
    }
  }

  @NotNull
  public static String getEmbeddedSuffix() {
    return "?var=embed" + (UIUtil.isUnderDarcula() ? "&theme=dark" : "");
  }
}
