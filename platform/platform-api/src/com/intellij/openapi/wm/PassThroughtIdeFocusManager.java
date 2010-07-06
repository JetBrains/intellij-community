/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Expirable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class PassThroughtIdeFocusManager extends IdeFocusManager {

  private static final PassThroughtIdeFocusManager ourInstance = new PassThroughtIdeFocusManager();

  public static PassThroughtIdeFocusManager getInstance() {
    return ourInstance;
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
    c.requestFocus();
    return new ActionCallback.Done();
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced) {
    return command.run();
  }

  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return comp;
  }

  public void doWhenFocusSettlesDown(@NotNull Runnable runnable) {
    runnable.run();
  }

  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    return null;
  }

  public boolean dispatch(KeyEvent e) {
    return false;
  }

  public ActionCallback requestDefaultFocus(boolean forced) {
    return new ActionCallback.Done();
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return true;
  }

  @Override
  public Expirable getTimestamp(boolean trackOnlyForcedCommands) {
    return new Expirable() {
      public boolean isExpired() {
        return false;
      }
    };
  }

  @Override
  public void suspendKeyProcessingUntil(@NotNull ActionCallback done) {
  }

  @Override
  public boolean isFocusBeingTransferred() {
    return false;
  }

}
