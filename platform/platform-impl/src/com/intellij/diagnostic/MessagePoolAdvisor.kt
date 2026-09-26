// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Internal
interface MessagePoolAdvisor : EventListener {
  /**
   * @return `false` to stop processing and do not add the message to the pool
   */
  suspend fun beforeEntryAdded(e: BeforeEntryAddedEvent): Boolean = true

  suspend fun afterEntryAdded(e: AfterEntryAddedEvent) {}

  fun poolCleared(e: PoolClearedEvent) {}
  fun entryWasRead(e: EntryReadEvent) {}

  class BeforeEntryAddedEvent(val message: AbstractMessage)
  class AfterEntryAddedEvent(val message: AbstractMessage)

  class PoolClearedEvent
  class EntryReadEvent(val message: AbstractMessage)
}
