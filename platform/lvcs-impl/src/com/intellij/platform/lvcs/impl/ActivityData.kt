// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class ActivityData(val items: List<ActivityItem>): UserDataHolderBase() {
  companion object {
    val EMPTY = ActivityData(emptyList())
  }
}