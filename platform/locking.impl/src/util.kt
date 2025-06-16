// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IntelliJLockingUtil")

package com.intellij.platform.locking.impl

import com.intellij.openapi.application.ThreadingSupport
import org.jetbrains.annotations.ApiStatus

private val instance = NestedLocksThreadingSupport()

@ApiStatus.Internal
fun newLockingSupport(): ThreadingSupport =
  NestedLocksThreadingSupport()

@ApiStatus.Internal
fun getGlobalThreadingSupport(): ThreadingSupport {
  return instance
}

@ApiStatus.Internal
fun getGlobalNestedLockingThreadingSupport(): NestedLocksThreadingSupport {
  return instance
}