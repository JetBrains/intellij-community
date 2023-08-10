// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.rpc

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.execution.process.mediator.daemon.QuotaState
import com.intellij.execution.process.mediator.rpc.QuotaOptions as QuotaOptionsMessage
import com.intellij.execution.process.mediator.rpc.QuotaState as QuotaStateMessage

private val EMPTY_INSTANCE = Empty.getDefaultInstance()

fun QuotaOptionsMessage.toQuotaOptions(): QuotaOptions {
  return QuotaOptions(timeLimitMs = timeLimitMs,
                      isRefreshable = isRefreshable)
}

fun QuotaOptionsMessage.Builder.buildFrom(quotaOptions: QuotaOptions): QuotaOptionsMessage {
  return apply {
    timeLimitMs = quotaOptions.timeLimitMs
    isRefreshable = quotaOptions.isRefreshable
  }.build()
}

fun QuotaStateMessage.toQuotaState(): QuotaState {
  return when {
    hasStateNew() -> QuotaState.New(quotaOptions.toQuotaOptions())
    hasStateActive() -> QuotaState.Active(quotaOptions.toQuotaOptions(),
                                          elapsedMs = stateActive.elapsedMs)
    else -> QuotaState.Expired
  }
}

fun QuotaStateMessage.Builder.buildFrom(quotaState: QuotaState): QuotaStateMessage {
  return apply {
    quotaOptions = QuotaOptionsMessage.newBuilder().buildFrom(quotaState.options)
    when (quotaState) {
      is QuotaState.New -> stateNew = EMPTY_INSTANCE
      is QuotaState.Active -> stateActive = QuotaStateActive.newBuilder().buildFrom(quotaState)
      QuotaState.Expired -> stateExpired = EMPTY_INSTANCE
    }
  }.build()
}

private fun QuotaStateActive.Builder.buildFrom(quotaState: QuotaState.Active): QuotaStateActive {
  return apply {
    elapsedMs = quotaState.elapsedMs
  }.build()
}
