// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class receives focus requests, manages the, and delegates to the awt focus subsystem. All focus requests
 * should be done through this class. For example, to request focus on a component:
 * <pre>
 *   IdeFocusManager.getInstance(project).requestFocus(comp, true);
 * </pre>
 * This is the preferred way to request focus on components to
 * <pre>
 *   comp.requestFocus();
 * </pre>
 *
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
   * Finds most suitable component to request focus to. For instance you may pass a JPanel instance,
   * this method will traverse into it's children to find focusable component
   * @return suitable component to focus
   */
  @Nullable
  public abstract JComponent getFocusTargetFor(@NotNull final JComponent comp);


  /**
   * Executes given runnable after all focus activities are finished
   */
  public abstract void doWhenFocusSettlesDown(@NotNull Runnable runnable);

  /**
   * Executes given runnable after all focus activities are finished, immediately or later with the given modality state
   */
  public abstract void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality);

  /**
   * Executes given runnable after all focus activities are finished
   */
  public abstract void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable);


  /**
   * Finds focused component among descendants of the given component. Descendants may be in child popups and windows
   */
  @Nullable
  public abstract Component getFocusedDescendantFor(final Component comp);

  @Deprecated
  // use #typeAheadUntil(ActionCallback, String) instead
  public void typeAheadUntil(ActionCallback done) {
    typeAheadUntil(done, "No cause has been provided");
  }

  /**
   * Aggregates all key events until given callback object is processed
   * @param done action callback
   */
  public void typeAheadUntil(ActionCallback done, @NotNull String cause) {}

  /**
   * Requests default focus. The method should not be called by the user code.
   */
  @NotNull
  public abstract ActionCallback requestDefaultFocus(boolean forced);

  /**
   * Reports of focus transfer is enabled right now. It can be disabled if app is inactive. In this case
   * all focus requests will be either postponed or executed only if {@code FocusCommand} can be executed on an inaactive app.
   * @see com.intellij.openapi.wm.FocusCommand#canExecuteOnInactiveApp()
   */
  public abstract boolean isFocusTransferEnabled();

  /**
   * Enables or disables typeahead
   * @see #typeAheadUntil(com.intellij.openapi.util.ActionCallback)
   */
  public abstract void setTypeaheadEnabled(boolean enabled);

  /**
   * Computes effective focus owner
   */
  public abstract Component getFocusOwner();

  /**
   * Runs runnable for which {@code DataContext} will no be computed from the current focus owner,
   * but used the given one
   */
  public abstract void runOnOwnContext(@NotNull DataContext context, @NotNull Runnable runnable);

  /**
   * Returns last focused component for the given {@code IdeFrame}
   */
  @Nullable
  public abstract Component getLastFocusedFor(@Nullable IdeFrame frame);

  /**
   * Returns last focused {@code IdeFrame}
   */
  @Nullable
  public abstract IdeFrame getLastFocusedFrame();

  /**
   * Put the container window to front. May not execute of the app is inactive or under some other conditions. This
   * is the preferred way to finding the container window and unconditionally calling {@code window.toFront()}
   */
  public abstract void toFront(JComponent c);

  public static IdeFocusManager getInstance(@Nullable Project project) {
    if (project == null || project.isDisposed() || !project.isInitialized()) return getGlobalInstance();

    return project.getComponent(IdeFocusManager.class);
  }

  @NotNull
  public static IdeFocusManager findInstanceByContext(@Nullable DataContext context) {
    IdeFocusManager instance = null;
    if (context != null) {
      instance = getInstanceSafe(CommonDataKeys.PROJECT.getData(context));
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
      return getInstance(project);
    }
    return null;
  }

  @NotNull
  public static IdeFocusManager findInstance() {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return owner != null ? findInstanceByComponent(owner) : findInstanceByContext(null);
  }

  @Deprecated
  @NotNull
  public FocusRequestor getFurtherRequestor() {
    return new FocusRequestor() {
      @NotNull
      @Override
      public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
        return ActionCallback.REJECTED;
      }

      @Override
      public void dispose() {}
    };
  }

  @NotNull
  public static IdeFocusManager getGlobalInstance() {
    IdeFocusManager fm = null;

    Application app = ApplicationManager.getApplication();
    if (app != null && app.hasComponent(IdeFocusManager.class)) {
      fm = app.getComponent(IdeFocusManager.class);
    }

    if (fm == null) {
      // happens when app is semi-initialized (e.g. when IDEA server dialog is shown)
      fm = PassThroughIdeFocusManager.getInstance();
    }

    return fm;
  }

}
