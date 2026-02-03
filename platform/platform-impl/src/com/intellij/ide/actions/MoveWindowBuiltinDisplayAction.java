// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.jetbrains.Extensions;
import com.jetbrains.GraphicsUtils;
import com.jetbrains.JBR;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@ApiStatus.Internal
public class MoveWindowBuiltinDisplayAction extends DumbAwareAction {

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment())
      return;

    GraphicsUtils grUtils = JBR.getGraphicsUtils(Extensions.BUILTIN_DISPLAY_CHECKER);
    if (grUtils == null)
      return;

    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    final GraphicsDevice currentDevice = activeFrame.getGraphicsConfiguration().getDevice();
    if (grUtils.isBuiltinDisplay(currentDevice))
      return;

    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    for (int i = 0; i < devices.length; i++) {
      if (grUtils.isBuiltinDisplay(devices[i])) {
        activeFrame.setLocation(devices[i].getDefaultConfiguration().getBounds().x, activeFrame.getY());
        break;
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return IdeEventQueue.getInstance().isDispatchingOnMainThread ? ActionUpdateThread.EDT : ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
    if (ApplicationManager.getApplication().isHeadlessEnvironment() || JBR.getGraphicsUtils(Extensions.BUILTIN_DISPLAY_CHECKER) == null)
      return;

    GraphicsEnvironment g = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] devices = g.getScreenDevices();
    if (devices == null)
      return;
    e.getPresentation().setEnabledAndVisible(devices.length > 1);
  }
}
