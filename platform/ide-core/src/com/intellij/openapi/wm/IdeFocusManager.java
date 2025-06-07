// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class receives focus requests, manages the, and delegates to the AWT focus subsystem.
 * <p>
 * <em>All focus requests should be done through this class.</em>
 * <p>
 * For example, to request focus on a component:
 * <pre>
 *   IdeFocusManager.getInstance(project).requestFocus(comp, true);
 * </pre>
 * This is the preferred way to request focus on components to
 * <pre>
 *   comp.requestFocus();
 * </pre>
 * <p>
 * This class is also responsible for delivering key events while focus transferring is in progress.
 * <p>
 * {@code IdeFocusManager} instance can be received per project or the global instance. The preferred way is
 * to use instance {@code IdeFocusManager.getInstance(project)}. If no project instance is available, then
 * {@code IdeFocusManager.getGlobalInstance()} can be used.
 */
public abstract class IdeFocusManager implements FocusRequestor {
  public ActionCallback requestFocusInProject(@NotNull Component c, @Nullable Project project) {
    return requestFocus(c, false);
  }

  /**
   * Finds most suitable component to request focus to. For instance, you may pass a JPanel instance,
   * this method will traverse into its children to find focusable component.
   *
   * @return suitable component to focus
   */
  public abstract @Nullable JComponent getFocusTargetFor(@NotNull JComponent comp);

  /**
   * @deprecated See deprecation notice for {@link #doWhenFocusSettlesDown(Runnable, ModalityState)}
   */
  @Deprecated
  public abstract void doWhenFocusSettlesDown(@NotNull Runnable runnable);

  /**
   * @deprecated This method doesn't do what it's expected to do.
   *   If called in EDT, it just invokes the target runnable directly, after performing expiration check for {@link ExpirableRunnable}
   *   and wrapping the execution into {@link WriteIntentReadAction}.<br>
   *   If you want to postpone the execution until focus transfers, requested in this or previous EDT events, will be completed, just use
   *   {@link SwingUtilities#invokeLater(Runnable)} or {@link Application#invokeLater(Runnable, ModalityState)}.
   *   Note that requests to transfer focus between windows cannot be reliably handled in this manner, as they are asynchronous in nature,
   *   and one cannot know when OS will grant them, if at all, at least on Linux.
   */
  @Deprecated
  public abstract void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality);

  /**
   * @deprecated See deprecation notice for {@link #doWhenFocusSettlesDown(Runnable, ModalityState)}
   */
  @Deprecated
  public abstract void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable);

  /**
   * Finds focused component among descendants of the given component. Descendants may be in child popups and windows.
   */
  public abstract @Nullable Component getFocusedDescendantFor(@NotNull Component comp);

  /**
   * Requests default focus. The method should not be called by the user code.
   */
  public @NotNull ActionCallback requestDefaultFocus(boolean forced) {
    return ActionCallback.DONE;
  }

  /**
   * Reports of focus transfer is enabled right now. It can be disabled if the app is inactive. In this case
   * all focus requests will be either postponed or executed if {@code FocusCommand} can be executed on an inactive app.
   */
  public abstract boolean isFocusTransferEnabled();

  /**
   * Computes effective focus owner.
   */
  public abstract @Nullable Component getFocusOwner();

  /**
   * Runs runnable for which {@code DataContext} will not be computed from the current focus owner,
   * but using given one.
   */
  public abstract void runOnOwnContext(@NotNull DataContext context, @NotNull Runnable runnable);

  /**
   * Returns last focused component for the given IDE {@code Window}.
   * Only IDE window (that's implementing {@link IdeFrame}).
   */
  public abstract @Nullable Component getLastFocusedFor(@Nullable Window frame);

  /**
   * Returns last focused {@code IdeFrame}.
   */
  public abstract @Nullable IdeFrame getLastFocusedFrame();

  public abstract @Nullable Window getLastFocusedIdeWindow();

  /**
   * Put the container window to front. May not execute if the app is inactive or under some other conditions. This
   * is the preferred way to finding the container window and unconditionally calling {@code window.toFront()}.
   */
  public abstract void toFront(JComponent c);

  public static IdeFocusManager getInstance(@Nullable Project project) {
    return getGlobalInstance();
  }

  public static @NotNull IdeFocusManager findInstanceByContext(@Nullable DataContext context) {
    return getGlobalInstance();
  }

  public static @NotNull IdeFocusManager findInstanceByComponent(@NotNull Component component) {
    return getGlobalInstance();
  }

  public static @NotNull IdeFocusManager findInstance() {
    return getGlobalInstance();
  }

  public static @NotNull IdeFocusManager getGlobalInstance() {
    IdeFocusManager focusManager = null;

    Application app = ApplicationManager.getApplication();
    if (app != null && LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      focusManager = app.getService(IdeFocusManager.class);
    }

    // happens when app is semi-initialized (e.g. when IDEA server dialog is shown)
    return focusManager == null ? PassThroughIdeFocusManager.getInstance() : focusManager;
  }

  @Override
  public void dispose() {
  }
}
