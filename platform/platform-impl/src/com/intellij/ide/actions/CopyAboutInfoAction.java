// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public class CopyAboutInfoAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    AboutPopup.AboutInformation information = new AboutPopup.AboutInformation();
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(information.getText()));
    }
    catch (Exception ignore) { }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setDescription("Copy information about " + ApplicationNamesInfo.getInstance().getFullProductName() + " to the clipboard");
  }
}
