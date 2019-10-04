// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

import static com.intellij.openapi.actionSystem.ActionPlaces.KEYBOARD_SHORTCUT;

public class CopyPathsAction extends AnAction implements DumbAware {
  public CopyPathsAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 0) {
      CopyPasteManager.getInstance().setContents(new StringSelection(getPaths(files)));
    }
  }

  private static String getPaths(VirtualFile[] files) {
    StringBuilder buf = new StringBuilder(files.length * 64);
    for (VirtualFile file : files) {
      if (buf.length() > 0) buf.append('\n');
      buf.append(file.getPresentableUrl());
    }
    return buf.toString();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (isCopyReferencePopupAvailable()) {
      event.getPresentation().setEnabledAndVisible(KEYBOARD_SHORTCUT.equals(event.getPlace()));
      return;
    }

    VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    int num = files != null ? files.length : 0;
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(num > 0);
    presentation.setVisible(num > 0 || !ActionPlaces.isPopupPlace(event.getPlace()));
    presentation.setText(IdeBundle.message(num == 1 ? "action.copy.path" : "action.copy.paths"));
  }

  public static boolean isCopyReferencePopupAvailable() {
    return !ApplicationManager.getApplication().isUnitTestMode() && Experiments.getInstance().isFeatureEnabled("copy.reference.popup");
  }
}
