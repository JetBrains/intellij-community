// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus.Internal

fun Logger.debugTrace(message: String) {
  if (isDebugEnabled()) {
    debug(message, Throwable())
  }
}

fun Logger.infoWithDebugTrace(message: String) {
  if (isDebugEnabled()) {
    debug(message, Throwable())
  }
  else {
    info(message)
  }
}