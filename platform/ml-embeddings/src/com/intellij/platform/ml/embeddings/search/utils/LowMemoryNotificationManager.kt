// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import java.util.concurrent.atomic.AtomicBoolean

@Service
class LowMemoryNotificationManager {
  private var isShownAndNotExpired = AtomicBoolean(false)

  fun showNotification() {
    // Make sure a user does not receive many notifications during the search.
    if (isShownAndNotExpired.compareAndSet(false, true)) {
      createNotification().notify(null)
    }
  }

  private fun createNotification(): Notification {
    val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        EmbeddingsBundle.getMessage("ml.embeddings.notification.indexing.low.memory"),
        "", NotificationType.WARNING
      )

    notification.whenExpired {
      isShownAndNotExpired.compareAndSet(true, false)
    }

    return notification
  }

  companion object {
    private const val NOTIFICATION_GROUP_ID = "Semantic search"

    fun getInstance(): LowMemoryNotificationManager = service()
  }
}