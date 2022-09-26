// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeEventQueue
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal class StatisticsStateCollectorsTrigger : AppLifecycleListener {
  override fun welcomeScreenDisplayed() {
    val ref = WeakReference(WelcomeFrame.getInstance())
    IdeEventQueue.getInstance().addIdleListener(object : Runnable {
      override fun run() {
        IdeEventQueue.getInstance().removeIdleListener(this) // need to detect only once
        // Only proceed if IDE opens with welcome screen and stays idle on it for some time
        if (WelcomeFrame.getInstance() != null && WelcomeFrame.getInstance() == ref.get()) {
          ApplicationManager.getApplication().coroutineScope.launch {
            FUStateUsagesLogger.getInstance().logApplicationStatesOnStartup()
          }
        }
      }
    }, WELCOME_SCREEN_IDLE_IN_MILLISECONDS)
  }

  companion object {
    private const val WELCOME_SCREEN_IDLE_IN_MILLISECONDS = 30000
  }
}