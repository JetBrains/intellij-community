// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MoveWindowToNextDisplayAction extends DumbAwareAction {

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    if (ApplicationManager.getApplication().isHeadlessEnvironment())
      return;

    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    String currentId = activeFrame.getGraphicsConfiguration().getDevice().getIDstring();

    for (int i = 0; i < devices.length; i++) {
      if (currentId.equals(devices[i].getIDstring())) {
        int next = i + 1 < devices.length ? i + 1 : 0;
        activeFrame.setLocation(devices[next].getDefaultConfiguration().getBounds().x, activeFrame.getY());
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
    if (ApplicationManager.getApplication().isHeadlessEnvironment())
      return;
    GraphicsEnvironment g = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] devices = g.getScreenDevices();
    if (devices == null)
      return;
    e.getPresentation().setEnabledAndVisible(devices.length > 1);
  }
}
