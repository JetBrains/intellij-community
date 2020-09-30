// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.lightEdit.intentions.openInProject.LightEditOpenInProjectIntention;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class LightEditOpenFileInProjectAction extends DumbAwareAction implements LightEditCompatible {
  public LightEditOpenFileInProjectAction() {
    super(ActionsBundle.messagePointer("action.LightEditOpenFileInProjectAction.text"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (!LightEdit.owns(e.getProject())) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      presentation.setVisible(true);
      VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
      presentation.setEnabled(file != null && file.isInLocalFileSystem());
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    Project project = LightEditUtil.requireLightEditProject(e.getProject());
    if (file != null) {
      LightEditOpenInProjectIntention.performOn(project, file);
    }
  }
}
