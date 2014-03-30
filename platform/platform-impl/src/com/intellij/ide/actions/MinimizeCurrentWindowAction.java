/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.mac.MacMainFrameDecorator;

import javax.swing.*;
import java.awt.*;

/**
 * User: spLeaner
 */
public class MinimizeCurrentWindowAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    if (focusOwner != null) {
      final Window window = focusOwner instanceof JFrame ? (Window) focusOwner : SwingUtilities.getWindowAncestor(focusOwner);
      if (window instanceof JFrame && !(((JFrame)window).getState() == Frame.ICONIFIED)) {
        ((JFrame)window).setState(Frame.ICONIFIED);
      }
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation p = e.getPresentation();
    p.setVisible(SystemInfo.isMac);

    if (SystemInfo.isMac) {
      Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
      if (project != null) {
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame != null) {
          JRootPane pane = frame.getRootPane();
          p.setEnabled(pane != null && pane.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) == null);
        }
      }
    }
    else {
      p.setEnabled(false);
    }
  }
}
