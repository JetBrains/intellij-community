// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework

import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions

object AutoSyncAssertions {

  enum class AutoSyncStatus { SYNCHRONIZED, PENDING }

  fun assertAutoSyncStatus(project: Project, expectedStatus: AutoSyncStatus) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(project)
    when (expectedStatus) {
      AutoSyncStatus.SYNCHRONIZED -> Assertions.assertEquals(false, notificationAware.isNotificationVisible()) {
        "Auto-sync notification should be hidden\n" +
        "  pending-projects=" + notificationAware.getProjectsWithNotification()
      }
      AutoSyncStatus.PENDING -> Assertions.assertEquals(true, notificationAware.isNotificationVisible()) {
        "Auto-sync notification should be shown"
      }
    }
  }
}