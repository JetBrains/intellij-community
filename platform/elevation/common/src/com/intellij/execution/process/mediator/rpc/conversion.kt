// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.rpc

import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.execution.process.mediator.rpc.QuotaOptions as QuotaOptionsMessage

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
