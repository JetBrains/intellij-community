// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff

import com.intellij.notification.impl.NotificationIdsHolder

class DiffNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      MERGE_INTERNAL_ERROR,
      EXTERNAL_TOO_MANY_SELECTED,
      EXTERNAL_CANT_LOAD_CHANGES
    )
  }

  companion object {
    const val MERGE_INTERNAL_ERROR = "diff.merge.intenral.error"
    const val EXTERNAL_TOO_MANY_SELECTED = "diff.external.too.many.selected"
    const val EXTERNAL_CANT_LOAD_CHANGES = "diff.external.cant.load.changes"
  }
}