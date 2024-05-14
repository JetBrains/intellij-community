// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.LocalHistoryNotificationIdsHolder.Companion.NOTIFICATION_GROUP_ID
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.impl.NotificationIdsHolder

internal class LocalHistoryNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(STORAGE_CORRUPTED)
  }

  companion object {
    const val NOTIFICATION_GROUP_ID = "LocalHistory.General"
    const val STORAGE_CORRUPTED = "lvcs.storage.corrupted"
  }
}

internal fun getLocalHistoryNotificationGroup(): NotificationGroup {
  return NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
}