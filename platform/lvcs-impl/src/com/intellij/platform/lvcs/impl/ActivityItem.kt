// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ActivityItem {
  val timestamp: Long
}