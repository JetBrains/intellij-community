// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.openapi.util.NlsContexts
import com.sun.jdi.event.LocatableEvent

interface LocatableEventRequestor : SuspendingRequestor {
  /**
   * @return true if the request was hit by the event, false otherwise
   */
  @Throws(EventProcessingException::class)
  fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean

  fun shouldIgnoreThreadFiltering(): Boolean {
    return false
  }

  class EventProcessingException(val title: @NlsContexts.DialogTitle String, message: String, cause: Throwable)
    : Exception(message, cause)
}
