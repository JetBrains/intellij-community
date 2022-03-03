// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeEventQueue;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

final class StatisticsStateCollectorsTrigger implements AppLifecycleListener {

  private static final int WELCOME_SCREEN_IDLE_IN_MILLISECONDS = 30_000;

  @Override
  public void welcomeScreenDisplayed() {
    final WeakReference<IdeFrame> ref = new WeakReference<>(WelcomeFrame.getInstance());
    IdeEventQueue.getInstance().addIdleListener(new Runnable() {
      @Override
      public void run() {
        IdeEventQueue.getInstance().removeIdleListener(this); // need to detect only once
        // Only proceed if IDE opens with welcome screen and stays idle on it for some time
        if (WelcomeFrame.getInstance() != null && WelcomeFrame.getInstance().equals(ref.get())) {
          JobScheduler.getScheduler().schedule(
            () -> FUStateUsagesLogger.create().logApplicationStatesOnStartup(),
            0, TimeUnit.SECONDS);
        }
      }
    }, WELCOME_SCREEN_IDLE_IN_MILLISECONDS);
  }
}
