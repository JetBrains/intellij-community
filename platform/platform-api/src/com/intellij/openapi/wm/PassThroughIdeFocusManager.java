// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class PassThroughIdeFocusManager extends IdeFocusManager {
  private static final PassThroughIdeFocusManager ourInstance = new PassThroughIdeFocusManager();

  public static PassThroughIdeFocusManager getInstance() {
    return ourInstance;
  }

  @Override
  @NotNull
  public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
    c.requestFocus();
    return ActionCallback.DONE;
  }

  @Override
  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return comp;
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality) {
    runnable.run();
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    if (!runnable.isExpired()) {
      runnable.run();
    }
  }

  @Override
  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    return null;
  }

  public boolean dispatch(@NotNull KeyEvent e) {
    return false;
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return true;
  }

  @Override
  public void setTypeaheadEnabled(boolean enabled) {
  }

  @Override
  public Component getFocusOwner() {
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
  }

  @Override
  public void runOnOwnContext(@NotNull DataContext context, @NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public Component getLastFocusedFor(IdeFrame frame) {
    return null;
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return null;
  }

  @Override
  public void toFront(JComponent c) {
  }
}
