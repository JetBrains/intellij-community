// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ToggleFullScreenAction extends DumbAwareAction implements LightEditCompatible, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    IdeFrameEx frame = getFrameHelper(e.getProject());
    if (frame != null) {
      frame.toggleFullScreen(!frame.isInFullScreen());
    }
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    Presentation p = e.getPresentation();

    IdeFrameEx frame = null;
    boolean isApplicable = WindowManager.getInstance().isFullScreenSupportedInCurrentOS();
    if (isApplicable) {
      frame = getFrameHelper(e.getProject());
      isApplicable = frame != null;
    }

    if (!ActionPlaces.MAIN_TOOLBAR.equals(e.getPlace())) {
      p.setVisible(isApplicable);
    }
    p.setEnabled(isApplicable);

    if (isApplicable) {
      p.setText(frame.isInFullScreen() ? ActionsBundle.message("action.ToggleFullScreen.text.exit")
                                       : ActionsBundle.message("action.ToggleFullScreen.text.enter"));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static @Nullable IdeFrameEx getFrameHelper(@Nullable Project project) {
    return WindowManagerEx.getInstanceEx().findFrameHelper(project);
  }
}
