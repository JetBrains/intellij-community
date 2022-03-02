// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IndexUpToDateCheckIn {
  private val upToDateCheckState = ThreadLocal<Int>()

  @JvmStatic
  fun <T, E : Throwable?> disableUpToDateCheckIn(runnable: ThrowableComputable<T, E>): T {
    disableUpToDateCheckForCurrentThread()
    return try {
      runnable.compute()
    }
    finally {
      enableUpToDateCheckForCurrentThread()
    }
  }

  @JvmStatic
  fun isUpToDateCheckEnabled(): Boolean {
    val value: Int? = upToDateCheckState.get()
    return value == null || value == 0
  }

  private fun disableUpToDateCheckForCurrentThread() {
    val currentValue = upToDateCheckState.get()
    upToDateCheckState.set(if (currentValue == null) 1 else currentValue.toInt() + 1)
  }

  private fun enableUpToDateCheckForCurrentThread() {
    val currentValue = upToDateCheckState.get()
    if (currentValue != null) {
      val newValue = currentValue.toInt() - 1
      if (newValue != 0) {
        upToDateCheckState.set(newValue)
      }
      else {
        upToDateCheckState.remove()
      }
    }
  }
}