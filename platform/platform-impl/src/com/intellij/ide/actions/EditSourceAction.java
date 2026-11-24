// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService;
import com.intellij.openapi.wm.ex.ProjectFrameCapability;
import org.jetbrains.annotations.NotNull;

public class EditSourceAction extends BaseNavigateToSourceAction {
  public EditSourceAction() {
    super(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // A project with no frame of its own has no editor to open the source in.
    Project project = e.getProject();
    if (project != null &&
        ApplicationManager.getApplication().getService(ProjectFrameCapabilitiesService.class)
          .has(project, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    super.update(e);

    if (e.getPlace().equals(ActionPlaces.ACTION_SEARCH)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }
}
