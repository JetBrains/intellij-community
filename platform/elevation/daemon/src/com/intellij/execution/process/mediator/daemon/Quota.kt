// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package com.intellij.execution.process.mediator.daemon


interface Quota {
  fun isExceeded(): Boolean
}

data class TimeQuota(
  val timeLimitMs: Long = UNLIMITED_MS,
  val isRefreshable: Boolean = true,
  private val startTimeMillis: Long = if (timeLimitMs == UNLIMITED_MS) 0 else System.currentTimeMillis(),
) : Quota {
  init {
    require(isUnlimited || timeLimitMs >= 0) { "timeLimitMs must ge non-negative or UNLIMITED" }
  }
  val isUnlimited get() = this.timeLimitMs == UNLIMITED_MS

  fun elapsed(): Long = if (isUnlimited) 0 else System.currentTimeMillis() - startTimeMillis
  fun remaining(): Long = if (isUnlimited) Long.MAX_VALUE else timeLimitMs - elapsed()

  override fun isExceeded(): Boolean = remaining() <= 0

  /**
   * This can only reduce the quota. An already exceeded quota doesn't change.
   * The [startTimeMillis] is not altered, use [refresh] instead.
   */
  fun adjust(other: TimeQuota): TimeQuota =
    if (isExceeded()) this
    else copy(
      timeLimitMs = when {
        isUnlimited -> other.timeLimitMs
        other.isUnlimited -> timeLimitMs
        else -> other.timeLimitMs.coerceAtMost(timeLimitMs)
      },
      isRefreshable = other.isRefreshable && isRefreshable,
    )

  fun refresh(): TimeQuota =
    if (!isRefreshable || isExceeded()) this else copy(startTimeMillis = System.currentTimeMillis())

  override fun hashCode(): Int = if (isUnlimited) 0 else super.hashCode()
  override fun equals(other: Any?): Boolean = other is TimeQuota && isUnlimited && other.isUnlimited || super.equals(other)

  companion object {
    const val UNLIMITED_MS: Long = -1L

    val UNLIMITED = TimeQuota()
    val EXCEEDED = TimeQuota(0, isRefreshable = false)
  }
}
