/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author pegov
 */
public class ToggleFullScreenAction extends DumbAwareAction {
  private static final String TEXT_ENTER_FULL_SCREEN = ActionsBundle.message("action.ToggleFullScreen.text.enter");
  private static final String TEXT_EXIT_FULL_SCREEN = ActionsBundle.message("action.ToggleFullScreen.text.exit");

  @Override
  public void actionPerformed(AnActionEvent e) {
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
    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    if (focusOwner != null) {
      Window window = focusOwner instanceof JFrame ? (Window) focusOwner : SwingUtilities.getWindowAncestor(focusOwner);
      if (!(window instanceof IdeFrameEx)) {
        window = SwingUtilities.getWindowAncestor(window);
      }
      if (window instanceof IdeFrameEx) {
        return (IdeFrameEx)window;
      }
    }
    return null;
  }
}
