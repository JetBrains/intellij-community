// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.search.actions;

import com.intellij.find.editorHeaderActions.StatusTextAction;
import com.intellij.largeFilesEditor.search.LfeSearchManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class LargeFileStatusTextAction extends StatusTextAction {
  private final LfeSearchManager searchManager;

  public LargeFileStatusTextAction(LfeSearchManager searchManager) {
    super();
    this.searchManager = searchManager;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    searchManager.updateStatusText();
    String statusText = searchManager.getStatusText();

    JLabel label = (JLabel)e.getPresentation().getClientProperty(COMPONENT_KEY);
    if (label != null) {
      label.setText(statusText);
      label.setVisible(StringUtil.isNotEmpty(statusText));
    }
  }
}
