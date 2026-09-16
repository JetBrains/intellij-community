package com.intellij.platform.buildScripts.concurrency

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock

/** Acquires the lock interruptibly and checks task cancellation before running [action]. */
@ApiStatus.Internal
fun <T> ReentrantLock.withLockInterruptibly(action: () -> T): T {
  lockInterruptibly()
  try {
    checkInterrupted()
    TaskScope.current.get()?.checkCancelled()
    TaskContext.current.get()?.checkCancelled()
    return action()
  }
  finally {
    unlock()
  }
}
