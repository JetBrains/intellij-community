// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.notification

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.FeatureUsageTrackerImpl
import com.intellij.ide.StatisticsNotificationManager
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.showConsentsAgreementIfNeeded
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.ui.BalloonLayoutImpl
import com.intellij.util.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Window

internal class StatisticsNotificationManagerImpl(private val coroutineScope: CoroutineScope) : StatisticsNotificationManager {
  override suspend fun showNotificationIfNeeded() {
    if (!shouldShowNotification()) {
      return
    }

    NotificationsConfigurationImpl.remove("SendUsagesStatistics")

    val disposable = Disposer.newDisposable()
    ApplicationManager.getApplication().messageBus.connect(disposable)
      .subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          if (isEmpty(WindowManagerEx.getInstanceEx().mostRecentFocusedWindow)) {
            Disposer.dispose(disposable)
            // the consents are read from the disk, so the dialog is shown from a coroutine and not from the EDT
            coroutineScope.launch {
              showNotification()
            }
          }
        }
      })
  }
}

private suspend fun shouldShowNotification(): Boolean {
  return serviceAsync<UsageStatisticsPersistenceComponent>().isShowNotification &&
         System.currentTimeMillis() - Time.WEEK > (FeatureUsageTracker.getInstance() as FeatureUsageTrackerImpl).firstRunTime
}

private suspend fun showNotification() {
  if (!showConsentsAgreementIfNeeded(ConsentOptions.condUsageStatsConsent())) {
    return
  }

  writeAction {
    UsageStatisticsPersistenceComponent.getInstance().isShowNotification = false
  }

  withContext(Dispatchers.IO) {
    StatisticsUploadAssistant.getEventLogStatisticsService("FUS").send()
  }
}

private fun isEmpty(window: Window?): Boolean {
  val layout = ProjectFrameHelper.getFrameHelper(window)?.getBalloonLayout()
  // do not show notification if others exist
  return layout is BalloonLayoutImpl && layout.isEmpty
}
