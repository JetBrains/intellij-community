// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.locking.impl.listeners

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface ErrorHandler {
  fun handleError(error: Throwable)
}