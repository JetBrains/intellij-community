// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package com.intellij.execution.process.mediator.daemon


interface Quota {
  fun isExceeded(): Boolean
}

class QuotaExceededException(message: String? = null) : IllegalStateException(message)


data class TimeQuota(
  val options: QuotaOptions,
  private val startTimeMillis: Long = if (options == QuotaOptions.EXCEEDED) 0 else System.currentTimeMillis(),
) : Quota {
  val isUnlimited get() = options.isUnlimited

  fun elapsed(): Long = if (isUnlimited) 0 else System.currentTimeMillis() - startTimeMillis
  fun remaining(): Long = if (isUnlimited) Long.MAX_VALUE else options.timeLimitMs - elapsed()

  override fun isExceeded(): Boolean = remaining() <= 0

  /**
   * This can only reduce the quota. An already exceeded quota doesn't change.
   * The [startTimeMillis] is not altered, use [refresh] instead.
   */
  fun adjust(newOptions: QuotaOptions): TimeQuota =
    if (isExceeded()) this
    else copy(options = options.adjust(newOptions))

  fun refresh(): TimeQuota =
    if (!options.isRefreshable || isExceeded()) this else copy(startTimeMillis = System.currentTimeMillis())

  companion object {
    val EXCEEDED = TimeQuota(QuotaOptions.EXCEEDED)
  }
}
