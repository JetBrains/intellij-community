// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.api

import org.jetbrains.annotations.ApiStatus

/**
 * Marks a usage (of any kind) as being potentially dynamic. Dynamic usages are grouped under a dedicated [com.intellij.usages.UsageGroup].
 */
@ApiStatus.Experimental
interface DynamicUsage {
  val isDynamic: Boolean
    get() = true
}
