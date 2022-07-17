// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs

import com.intellij.notification.impl.NotificationIdsHolder

class DvcsNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      BRANCH_OPERATIONS_ON_ALL_ROOTS
    )
  }

  companion object {
    const val BRANCH_OPERATIONS_ON_ALL_ROOTS = "vcs.branch.operations.are.executed.on.all.roots"
  }
}