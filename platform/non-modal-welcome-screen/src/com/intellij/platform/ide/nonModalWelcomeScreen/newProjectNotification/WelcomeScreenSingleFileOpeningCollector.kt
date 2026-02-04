// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.newProjectNotification

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WelcomeScreenSingleFileOpeningCollector : CounterUsagesCollector() {

  enum class OpeningStrategy {
     FOLDER,
     PROJECT,
   }

  private val GROUP = EventLogGroup("welcome.screen.single.file.opening", 1)

  private val fieldOpeningStrategy: EnumEventField<OpeningStrategy> = EventFields.Enum("opening_strategy", OpeningStrategy::class.java)

  private val NOTIFICATION_SUPPRESSED = GROUP.registerEvent(
    "notification.suppressed",
  )

  private val NOTIFICATION_SHOWN = GROUP.registerEvent(
    "notification.shown",
    fieldOpeningStrategy
  )

  private val NOTIFICATION_CLOSED = GROUP.registerEvent(
    "notification.closed",
    fieldOpeningStrategy
  )

  private val NOTIFICATION_CLICKED = GROUP.registerEvent(
    "notification.clicked",
    fieldOpeningStrategy
  )

  override fun getGroup(): EventLogGroup = GROUP

  fun logNotificationSuppressed(): Unit = NOTIFICATION_SUPPRESSED.log()

  fun logNotificationShown(openingStrategy: OpeningStrategy): Unit = NOTIFICATION_SHOWN.log(openingStrategy)

  fun logNotificationClosed(openingStrategy: OpeningStrategy): Unit = NOTIFICATION_CLOSED.log(openingStrategy)

  fun logNotificationOpenButtonClicked(openingStrategy: OpeningStrategy): Unit = NOTIFICATION_CLICKED.log(openingStrategy)
}