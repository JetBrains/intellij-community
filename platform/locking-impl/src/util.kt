// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IntelliJLockingUtil")

package com.intellij.platform.locking.impl

import com.intellij.openapi.application.ThreadingSupport
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun newLockingSupport(): ThreadingSupport =
  NestedLocksThreadingSupport()