// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.api

/**
 * Marks a usage (of any kind) as being potentially dynamic. Dynamic usages are grouped under a dedicated [com.intellij.usages.UsageGroup].
 */
interface DynamicUsage {
  val isDynamic: Boolean
    get() = true
}
