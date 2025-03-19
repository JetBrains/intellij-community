// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

enum class NotificationAnnouncingMode(val stringValue: String) {
  NONE("none"),
  MEDIUM("medium"),
  HIGH("high");

  companion object {
    @JvmStatic
    fun get(stringValue: String?): NotificationAnnouncingMode? = stringValue?.let {
      values().firstOrNull {
        it.stringValue == stringValue
      }
    }
  }
}