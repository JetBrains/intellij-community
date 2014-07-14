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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.*;
import java.awt.*;

/**
 * User: spLeaner
 */
public class MinimizeCurrentWindowAction extends MacWindowActionBase {
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
}
