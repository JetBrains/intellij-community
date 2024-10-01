// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author ignatov
 */
@ApiStatus.Internal
public final class ZoomCurrentWindowAction extends MacWindowActionBase {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
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
