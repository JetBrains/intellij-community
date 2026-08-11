// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import org.jetbrains.annotations.ApiStatus

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Experimental
enum class NotificationLocation(val isTop: Boolean, val isLeft: Boolean) {
  TOP_RIGHT    (isTop = true,  isLeft = false),
  TOP_LEFT     (isTop = true,  isLeft = true ),
  BOTTOM_RIGHT (isTop = false, isLeft = false),
  BOTTOM_LEFT  (isTop = false, isLeft = true );

  val stringValue: String get() = name.lowercase()

  companion object {
    @JvmStatic
    fun getLocation(stringValue: String?): NotificationLocation? = stringValue?.let {
      entries.firstOrNull { it.stringValue == stringValue }
    }

    @JvmStatic
    //todo[kb] add ability to override this default from JVM options. For example, for an IDE based on IJ Platform
    fun getDefaultLocation(): NotificationLocation = BOTTOM_RIGHT
  }
}
