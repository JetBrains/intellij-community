// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.notification

import com.intellij.notification.impl.NotificationIdsHolder

class CollaborationToolsNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(REVIEW_BRANCH_CHECKOUT_FAILED)

  companion object {
    const val REVIEW_BRANCH_CHECKOUT_FAILED = "review.branch.checkout.failed"
  }
}