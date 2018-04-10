/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Expirable;
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

  @NotNull
  public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
    c.requestFocus();
    return ActionCallback.DONE;
  }

  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return comp;
  }

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

  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    return null;
  }

  public boolean dispatch(@NotNull KeyEvent e) {
    return false;
  }

  @NotNull
  public ActionCallback requestDefaultFocus(boolean forced) {
    return ActionCallback.DONE;
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

  @Override
  public void dispose() {
  }
}
