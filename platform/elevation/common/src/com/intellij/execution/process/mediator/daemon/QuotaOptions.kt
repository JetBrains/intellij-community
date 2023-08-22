// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import java.util.*

data class QuotaOptions(
  val timeLimitMs: Long = UNLIMITED_MS,
  val isRefreshable: Boolean = true,
) {
  init {
    require(isUnlimited || timeLimitMs >= 0) { "timeLimitMs must ge non-negative or UNLIMITED" }
  }

  val isUnlimited get() = this.timeLimitMs == UNLIMITED_MS

  /**
   * This can only reduce the quota.
   */
  fun adjust(other: QuotaOptions): QuotaOptions =
    copy(
      timeLimitMs = when {
        isUnlimited -> other.timeLimitMs
        other.isUnlimited -> timeLimitMs
        else -> other.timeLimitMs.coerceAtMost(timeLimitMs)
      },
      isRefreshable = other.isRefreshable && isRefreshable,
    )

  override fun hashCode(): Int = if (isUnlimited) 0 else Objects.hash(timeLimitMs, isRefreshable)
  override fun equals(other: Any?): Boolean {
    if (other !is QuotaOptions) return false
    return isUnlimited && other.isUnlimited ||
           (timeLimitMs == other.timeLimitMs &&
            isRefreshable == other.isRefreshable)
  }

  companion object {
    const val UNLIMITED_MS: Long = -1L

    val UNLIMITED = QuotaOptions()
    val EXCEEDED = QuotaOptions(0, isRefreshable = false)
  }
}