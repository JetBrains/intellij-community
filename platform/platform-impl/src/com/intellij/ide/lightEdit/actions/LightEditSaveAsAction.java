// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class LightEditSaveAsAction extends DumbAwareAction implements LightEditCompatible {
  public LightEditSaveAsAction() {
    super(ActionsBundle.messagePointer("action.SaveAs.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file != null) {
      LightEditService.getInstance().saveToAnotherFile(file);
    }
  }

}
