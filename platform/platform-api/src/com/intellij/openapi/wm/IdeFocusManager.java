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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Expirable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class IdeFocusManager {

  /**
   * Requests focus on a component
   * @param c
   * @param forced
   * @return action callback that either notifies when the focus was obtained or focus request was droppped
   */
  @NotNull
  public abstract ActionCallback requestFocus(@NotNull Component c, boolean forced);

  /**
   * Runs a request focus command, actual focus request is defined by the user in the command itself
   * @param command
   * @param forced
   * @return action callback that either notifies when the focus was obtained or focus request was droppped
   */
  @NotNull
  public abstract ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced);

  /**
   * Finds most suitable component to request focus to. For instance you may pass a JPanel instance,
   * this method will traverse into it's children to find focusable component
   * @param comp
   * @return suitable component to focus
   */
  @Nullable
  public abstract JComponent getFocusTargetFor(@NotNull final JComponent comp);


  public abstract void doWhenFocusSettlesDown(@NotNull Runnable runnable);

  @Nullable
  public abstract Component getFocusedDescendantFor(final Component comp);

  public abstract boolean dispatch(KeyEvent e);

  public abstract void suspendKeyProcessingUntil(@NotNull ActionCallback done);

  public abstract boolean isFocusBeingTransferred();

  public abstract ActionCallback requestDefaultFocus(boolean forced);

  public abstract boolean isFocusTransferEnabled();

  public static IdeFocusManager getInstance(@Nullable Project project) {
    if (project == null) return getGlobalInstance();

    if (project.isDisposed() || !project.isInitialized()) return getGlobalInstance();
    return project.getComponent(IdeFocusManager.class);
  }

  @NotNull
  public static IdeFocusManager findInstanceByContext(@Nullable DataContext context) {
    IdeFocusManager instance = null;
    if (context != null) {
      instance = getInstanceSafe(PlatformDataKeys.PROJECT.getData(context));
    }

    if (instance == null) {
      instance = findByComponent(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow());
    }

    if (instance == null) {
      instance = getGlobalInstance();
    }

    return instance;
  }

  @NotNull
  public static IdeFocusManager findInstanceByComponent(@NotNull Component c) {
    final IdeFocusManager instance = findByComponent(c);
    return instance != null ? instance : findInstanceByContext(null);
  }


  @Nullable
  private static IdeFocusManager findByComponent(Component c) {
    final Component parent = UIUtil.findUltimateParent(c);
    if (parent instanceof IdeFrame) {
      return getInstanceSafe(((IdeFrame)parent).getProject());
    }
    return null;
  }


  @Nullable
  private static IdeFocusManager getInstanceSafe(@Nullable Project project) {
    if (project != null && !project.isDisposed() && project.isInitialized()) {
      return IdeFocusManager.getInstance(project);
    } else {
      return null;
    }
  }

  @NotNull
  public static IdeFocusManager findInstance() {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return owner != null ? findInstanceByComponent(owner) : findInstanceByContext(null);
  }

  public abstract Expirable getTimestamp(boolean trackOnlyForcedCommands);

  @NotNull
  public static IdeFocusManager getGlobalInstance() {
    Application app = ApplicationManager.getApplication();
    IdeFocusManager fm = app != null ? app.getComponent(IdeFocusManager.class) : PassThroughtIdeFocusManager.getInstance();

    // It happens when IDEA server dialog is shown, app != null but it's semi-initialized
    if (fm == null) {
      fm = PassThroughtIdeFocusManager.getInstance();
    }

    return fm;
  }

  public Component getFocusOwner() {
    return isFocusBeingTransferred() ? null : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
  }
}
