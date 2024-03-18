// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.utils

import java.util.concurrent.locks.Lock

/**
 * Wrapper around [delegateSequenceProvider] that performs iteration under a single lock provided by [lockProvider].
 * To make sure the lock is acquired and released in the same thread,
 * iteration over this sequence should happen in a single thread.
 * To achieve this behavior in the coroutine context, run iteration with [kotlinx.coroutines.newSingleThreadContext]
 */
class LockedSequenceWrapper<T : Any>(private val lockProvider: () -> Lock,
                                     private val delegateSequenceProvider: () -> Sequence<T>) : Sequence<T> {
  override fun iterator(): Iterator<T> {
    val lock = lockProvider()
    lock.lock()

    var delegate: Iterator<T>? = null
    try {
      delegate = delegateSequenceProvider().iterator()
    }
    finally {
      if (delegate == null) {
        lock.unlock()
      }
    }

    return object : Iterator<T> {
      override fun hasNext(): Boolean {
        var delegateHasNext = false
        try {
          delegateHasNext = delegate!!.hasNext()
        }
        finally {
          if (!delegateHasNext) {
            // exception or no next element
            lock.unlock()
          }
        }
        return delegateHasNext
      }

      override fun next(): T {
        lateinit var delegateNext: T
        var success = false
        try {
          delegateNext = delegate!!.next()
          success = true
        }
        finally {
          if (!success) {
            // exception
            lock.unlock()
          }
        }
        return delegateNext
      }
    }
  }
}