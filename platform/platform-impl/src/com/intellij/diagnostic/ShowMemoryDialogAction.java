// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class ShowMemoryDialogAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (VMOptions.getWriteFile() == null) {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    EditXmxVMOptionDialog memoryDialog = new EditXmxVMOptionDialog();
    memoryDialog.show();
  }
}
