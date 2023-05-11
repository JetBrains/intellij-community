// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.api

/**
 * Marks a usage (of any kind) as having a read-write [UsageAccess]. Read-write usages are marked with an icon when previewed, and can be
 * filtered using the read-write filtering rules.
 */
interface ReadWriteUsage {
  fun computeAccess(): UsageAccess?
}
