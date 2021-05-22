// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

sealed class QuotaState {
  abstract val options: QuotaOptions

  data class New(
    override val options: QuotaOptions,
  ) : QuotaState()

  data class Active(
    override val options: QuotaOptions,
    val elapsedMs: Long,
  ) : QuotaState() {
    fun remaining(): Long = if (options.isUnlimited) Long.MAX_VALUE else options.timeLimitMs - elapsedMs
    fun isExpired(): Boolean = remaining() <= 0
  }

  object Expired : QuotaState() {
    override val options: QuotaOptions
      get() = QuotaOptions.EXCEEDED
  }
}
