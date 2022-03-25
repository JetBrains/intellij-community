// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import org.mockito.Mockito

/**
 * Originally from `com.android.testutils.MockitoKt`.
 *
 * @see Mockito.eq
 */
fun <T> eq(value: T): T {
  Mockito.eq(value)
  return value
}
