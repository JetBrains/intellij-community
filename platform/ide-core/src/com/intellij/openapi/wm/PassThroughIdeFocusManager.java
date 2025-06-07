// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class PassThroughIdeFocusManager extends IdeFocusManager {
  private static final PassThroughIdeFocusManager ourInstance = new PassThroughIdeFocusManager();

  public static PassThroughIdeFocusManager getInstance() {
    return ourInstance;
  }

  @Override
  public @NotNull ActionCallback requestFocus(@NotNull Component c, boolean forced) {
    c.requestFocus();
    return ActionCallback.DONE;
  }

  @Override
  public JComponent getFocusTargetFor(@NotNull JComponent component) {
    return component;
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
  public Component getFocusedDescendantFor(@NotNull Component component) {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focused == null) return null;

    if (focused == component || SwingUtilities.isDescendingFrom(focused, component)) return focused;

    return null;
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return true;
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
  public Component getLastFocusedFor(@Nullable Window frame) {
    return null;
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return null;
  }

  @Override
  public @Nullable Window getLastFocusedIdeWindow() {
    return null;
  }

  @Override
  public void toFront(JComponent c) {
  }
}
