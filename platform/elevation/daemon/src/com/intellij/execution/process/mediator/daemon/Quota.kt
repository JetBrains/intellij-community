// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package com.intellij.execution.process.mediator.daemon


interface Quota {
  fun isExceeded(): Boolean
}

class QuotaExceededException(message: String? = null) : IllegalStateException(message)


data class TimeQuota(
  val options: TimeQuotaOptions,
  private val startTimeMillis: Long = if (options == TimeQuotaOptions.EXCEEDED) 0 else System.currentTimeMillis(),
) : Quota {
  val isUnlimited get() = options.isUnlimited

  fun elapsed(): Long = if (isUnlimited) 0 else System.currentTimeMillis() - startTimeMillis
  fun remaining(): Long = if (isUnlimited) Long.MAX_VALUE else options.timeLimitMs - elapsed()

  override fun isExceeded(): Boolean = remaining() <= 0

  /**
   * This can only reduce the quota. An already exceeded quota doesn't change.
   * The [startTimeMillis] is not altered, use [refresh] instead.
   */
  fun adjust(newOptions: TimeQuotaOptions): TimeQuota =
    if (isExceeded()) this
    else copy(options = options.adjust(newOptions))

  fun refresh(): TimeQuota =
    if (!options.isRefreshable || isExceeded()) this else copy(startTimeMillis = System.currentTimeMillis())

  companion object {
    val EXCEEDED = TimeQuota(TimeQuotaOptions.EXCEEDED)
  }
}

data class TimeQuotaOptions(
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
  fun adjust(other: TimeQuotaOptions): TimeQuotaOptions =
    copy(
      timeLimitMs = when {
        isUnlimited -> other.timeLimitMs
        other.isUnlimited -> timeLimitMs
        else -> other.timeLimitMs.coerceAtMost(timeLimitMs)
      },
      isRefreshable = other.isRefreshable && isRefreshable,
    )

  override fun hashCode(): Int = if (isUnlimited) 0 else super.hashCode()
  override fun equals(other: Any?): Boolean = other is TimeQuotaOptions && isUnlimited && other.isUnlimited || super.equals(other)

  companion object {
    const val UNLIMITED_MS: Long = -1L

    val UNLIMITED = TimeQuotaOptions()
    val EXCEEDED = TimeQuotaOptions(0, isRefreshable = false)
  }
}
