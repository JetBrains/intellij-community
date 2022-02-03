// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;

final class FrameStateManagerAppListener implements ApplicationActivationListener {
  private final FrameStateListener publisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(FrameStateListener.TOPIC);

  @Override
  public void applicationActivated(@NotNull IdeFrame ideFrame) {
    System.setProperty("com.jetbrains.suppressWindowRaise", "false");
    publisher.onFrameActivated();
    // don't fire events when welcome screen is activated/deactivated
    if (ideFrame instanceof IdeFrameImpl) {
      LifecycleUsageTriggerCollector.onFrameActivated(ideFrame.getProject());
    }
  }

  @Override
  public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
    System.setProperty("com.jetbrains.suppressWindowRaise", "true");
    if (ApplicationManager.getApplication().isDisposed()) {
      return;
    }

    // don't fire events when welcome screen is activated/deactivated
    if (ideFrame instanceof IdeFrameImpl) {
      LifecycleUsageTriggerCollector.onFrameDeactivated(ideFrame.getProject());
    }
    publisher.onFrameDeactivated();
  }
}
