// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdleTracker
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

private class StatisticsStateCollectorsTrigger : AppLifecycleListener {
  @OptIn(FlowPreview::class)
  override fun welcomeScreenDisplayed() {
    val ref = WeakReference(WelcomeFrame.getInstance())
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      // init service
      serviceAsync<FUCounterUsageLogger>()

      IdleTracker.getInstance().events
        .debounce(30.seconds)
        // need to detect only once
        .first()

      // only proceed if the IDE opens with a welcome screen and stays idle on it for some time
      val welcomeFrame = WelcomeFrame.getInstance()
      if (welcomeFrame != null && welcomeFrame == ref.get()) {
        serviceAsync<FUStateUsagesLogger>().logApplicationStatesOnStartup()
      }
    }
  }
}