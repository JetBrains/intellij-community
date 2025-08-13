// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.diagnostic.Logger
import java.util.function.Consumer

internal class UpdateRescheduler(private val defaultDelay: Long, private val delayDelta: Long, private val maxAttempts: Int, private val logger: Logger, private val rescheduleAction: Consumer<Long>) {
  private fun getDelay() = attempts * delayDelta + defaultDelay
  private var attempts = 0

  fun rescheduleIfNeeded(rescheduleNeeded: Boolean) {
    if (rescheduleNeeded) {
      if (attempts >= maxAttempts) {
        logger.warn("Reschedule update attempts exceeded limit=$maxAttempts;")
        return
      }
      rescheduleAction.accept(getDelay())
      logger.debug("Update scheduled")
      attempts++
    } else {
      reset()
    }
  }

  fun reset() {
    attempts = 0
  }
}