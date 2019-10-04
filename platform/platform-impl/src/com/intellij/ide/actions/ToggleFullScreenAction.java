// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author pegov
 */
final class ToggleFullScreenAction extends DumbAwareAction {
  private static final String TEXT_ENTER_FULL_SCREEN = ActionsBundle.message("action.ToggleFullScreen.text.enter");
  private static final String TEXT_EXIT_FULL_SCREEN = ActionsBundle.message("action.ToggleFullScreen.text.exit");

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    IdeFrameEx frame = getFrame();
    if (frame != null) {
      frame.toggleFullScreen(!frame.isInFullScreen());
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Presentation p = e.getPresentation();

    IdeFrameEx frame = null;
    boolean isApplicable = WindowManager.getInstance().isFullScreenSupportedInCurrentOS() && (frame = getFrame()) != null;

    if (e.getPlace() != ActionPlaces.MAIN_TOOLBAR) {
      p.setVisible(isApplicable);
    }
    p.setEnabled(isApplicable);

    if (isApplicable) {
      p.setText(frame.isInFullScreen() ? TEXT_EXIT_FULL_SCREEN : TEXT_ENTER_FULL_SCREEN);
    }
  }

  @Nullable
  private static IdeFrameEx getFrame() {
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    return frame instanceof IdeFrameEx ? ((IdeFrameEx)frame) : null;
  }
}
