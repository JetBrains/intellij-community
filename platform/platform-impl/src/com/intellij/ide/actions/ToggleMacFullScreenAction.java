/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author pegov
 */
public class ToggleMacFullScreenAction extends AnAction implements DumbAware {

  private static final String TEXT_ENTER_FULLSCREEN = ActionsBundle.message("action.ToggleFullScreen.text.enter");
  private static final String TEXT_EXIT_FULL_SCREEN = ActionsBundle.message("action.ToggleFullScreen.text.exit");

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Frame frame = getFrame();
    if (frame instanceof IdeFrameImpl) {
      ((IdeFrameImpl)frame).getFrameDecorator().toggleFullScreen();
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation p = e.getPresentation();

    final boolean isApplicable = SystemInfo.isMac && SystemInfo.isMacOSLion;

    p.setVisible(isApplicable);
    p.setEnabled(isApplicable);

    if (isApplicable) {
      final Frame frame = getFrame();
      final boolean isInFullScreen = frame != null && WindowManagerEx.getInstanceEx().isFullScreen(frame);
      p.setText(isInFullScreen ? TEXT_EXIT_FULL_SCREEN : TEXT_ENTER_FULLSCREEN);
    }
  }

  @Nullable
  private Frame getFrame() {
    final Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    if (focusOwner != null) {
      final Window window = focusOwner instanceof JFrame ? (Window) focusOwner : SwingUtilities.getWindowAncestor(focusOwner);
      if (window instanceof JFrame) {
        return (Frame)window;
      }
    }
    return null;
  }

}
