// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class WindowAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend, DumbAware {

  private static final String NO_WINDOW_ACTIONS = "no.window.actions";
  private static JLabel ourSizeHelper;

  public static void setEnabledFor(@Nullable Window window, boolean enabled) {
    JRootPane root = getRootPane(window);
    if (root != null) root.putClientProperty(NO_WINDOW_ACTIONS, !enabled);
  }

  {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public final void update(@NotNull AnActionEvent event) {
    @Nullable Component component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Window window = ComponentUtil.getWindow(component);
    boolean enabled = isEnabledFor(window);
    if (enabled && Registry.is("no.window.actions.in.editor")) {
      Editor editor = event.getData(CommonDataKeys.EDITOR);
      enabled = editor == null || !editor.getContentComponent().hasFocus();
    }
    event.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabledFor(@Nullable Window window) {
    if (window == null || window instanceof IdeFrame) return false;
    if (window instanceof Dialog && !((Dialog)window).isResizable()) return false;
    JRootPane root = getRootPane(window);
    if (root == null) return true;
    Object property = root.getClientProperty(NO_WINDOW_ACTIONS);
    return property == null || !property.toString().equals("true");
  }

  private static @Nullable JRootPane getRootPane(@Nullable Window window) {
    if (window instanceof RootPaneContainer container) {
      return container.getRootPane();
    }
    return null;
  }

  private static void performSizeAction(@NotNull AnActionEvent e, boolean horizontal, boolean positive) {
    @Nullable Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Window window = ComponentUtil.getWindow(component);
    if (window == null) return;
    Dimension size = getPreferredDelta();
    int baseValue = horizontal ? size.width : size.height;
    int inc = baseValue *
              Registry.intValue(horizontal ? "ide.windowSystem.hScrollChars" : "ide.windowSystem.vScrollChars");
    if (!positive) {
      inc = -inc;
    }

    Rectangle bounds = window.getBounds();
    if (horizontal) {
      bounds.width += inc;
    }
    else {
      bounds.height += inc;
    }
    window.setBounds(bounds);
  }

  static @NotNull Dimension getPreferredDelta() {
    if (ourSizeHelper == null) {
      ourSizeHelper = new JLabel("W"); //NON-NLS
    }
    return ourSizeHelper.getPreferredSize();
  }

  public static final class IncrementWidth extends WindowAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      performSizeAction(e, true, true);
    }
  }

  public static final class DecrementWidth extends WindowAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      performSizeAction(e, true, false);
    }
  }

  public static final class IncrementHeight extends WindowAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      performSizeAction(e, false, true);
    }
  }

  public static final class DecrementHeight extends WindowAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      performSizeAction(e, false, false);
    }
  }
}
