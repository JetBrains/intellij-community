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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author ignatov
 */
public class ZoomCurrentWindowAction extends MacWindowActionBase {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    if (focusOwner != null) {
      final Window window = focusOwner instanceof JFrame ? (Window)focusOwner : SwingUtilities.getWindowAncestor(focusOwner);
      if (window instanceof JFrame frame) {
        if (frame.getExtendedState() == Frame.NORMAL) {
          frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
        else if (frame.getExtendedState() == Frame.MAXIMIZED_BOTH) {
          frame.setExtendedState(Frame.NORMAL);
        }
      }
    }
  }
}
