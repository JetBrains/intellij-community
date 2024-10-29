// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.WindowEvent;

final class FrameStateManagerAppListener implements ApplicationActivationListener {
  private final FrameStateListener publisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(FrameStateListener.TOPIC);

  private FrameStateManagerAppListener() {
    Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
      if (e.getID() != WindowEvent.WINDOW_ACTIVATED && e.getID() != WindowEvent.WINDOW_DEACTIVATED) {
        return;
      }

      if (IdeEventQueueKt.getSkipWindowDeactivationEvents()) {
        Logger.getInstance(FrameStateManagerAppListener.class).warn("Skipped " + e);
        return;
      }

      var windowEvent = (WindowEvent)e;
      var frame = ProjectUtil.getRootFrameForWindow(windowEvent.getWindow());
      if (frame == null) {
        return;
      }

      var otherFrame = ProjectUtil.getRootFrameForWindow(windowEvent.getOppositeWindow());
      if (frame == otherFrame) {
        return;
      }

      if (isDisposed()) {
        return;
      }

      if (e.getID() == WindowEvent.WINDOW_ACTIVATED) {
        publisher.onFrameActivated(frame);
      }
      else {
        publisher.onFrameDeactivated(frame);
      }
    }, AWTEvent.WINDOW_EVENT_MASK);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void applicationActivated(@NotNull IdeFrame ideFrame) {
    if (isDisposed()) {
      return;
    }

    publisher.onFrameActivated();

    // don't fire events when welcome screen is activated/deactivated
    if (ideFrame instanceof IdeFrameImpl) {
      LifecycleUsageTriggerCollector.onFrameActivated(ideFrame.getProject());
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
    if (isDisposed()) {
      return;
    }

    // don't fire events when welcome screen is activated/deactivated
    if (ideFrame instanceof IdeFrameImpl) {
      LifecycleUsageTriggerCollector.onFrameDeactivated(ideFrame.getProject());
    }

    publisher.onFrameDeactivated();
  }

  private static boolean isDisposed() {
    var app = ApplicationManager.getApplication();
    return app == null || app.isDisposed();
  }
}
