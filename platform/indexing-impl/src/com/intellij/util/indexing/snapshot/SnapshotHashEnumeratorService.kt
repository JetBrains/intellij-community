// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.hash.ContentHashEnumerator
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.io.IOUtil
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class SnapshotHashEnumeratorService : Closeable {
  companion object {
    private val LOG = logger<SnapshotHashEnumeratorService>()

    @JvmStatic
    fun getInstance(): SnapshotHashEnumeratorService = service()

    @JvmStatic
    fun closeIfCreated() {
      serviceIfCreated<SnapshotHashEnumeratorService>()?.close()
    }
  }

  private enum class State { OPEN, OPEN_AND_CLEAN, CLOSED }

  interface HashEnumeratorHandle {
    @Throws(IOException::class)
    fun enumerateHash(digest: ByteArray): Int
    fun release()
  }

  private inner class HashEnumeratorHandleImpl(private val requestorIndexId: ID<*, *>): HashEnumeratorHandle {
    override fun enumerateHash(digest: ByteArray): Int = contentHashEnumerator!!.enumerate(digest)

    override fun release() {
      lock.withLock {
        handles.remove(this)
        LOG.assertTrue(state != State.CLOSED, "handle is released for closed enumerator")
      }
    }

    override fun equals(other: Any?): Boolean {
      return other is HashEnumeratorHandleImpl && other.requestorIndexId == requestorIndexId
    }

    override fun hashCode(): Int {
      return requestorIndexId.hashCode()
    }

    override fun toString(): String {
      return "handle for ${requestorIndexId.name}"
    }
  }

  @Volatile
  private var state: State = State.CLOSED

  @Volatile
  private var contentHashEnumerator: ContentHashEnumerator? = null

  private val handles: MutableSet<HashEnumeratorHandle> = HashSet()

  private val lock: Lock = ReentrantLock()

  @Throws(IOException::class)
  fun initialize(): Boolean {
    lock.withLock {
      if (state == State.CLOSED) {
        val hashEnumeratorFile = IndexInfrastructure.getPersistentIndexRoot().resolve("textContentHashes")
        state = State.OPEN
        contentHashEnumerator =
          IOUtil.openCleanOrResetBroken({ ContentHashEnumerator(hashEnumeratorFile) },
                                        {
                                          IOUtil.deleteAllFilesStartingWith(hashEnumeratorFile)
                                          state = State.OPEN_AND_CLEAN
                                        })!!

      }
      LOG.assertTrue(state != State.CLOSED)
      return state == State.OPEN
    }
  }

  @Throws(IOException::class)
  override fun close() {
    lock.withLock {
      if (state == State.OPEN) {
        contentHashEnumerator!!.close()
        state = State.CLOSED

        LOG.assertTrue(handles.isEmpty(), "enumerator handles are still held: $handles")
        handles.clear()
      }
    }
  }

  fun flush() {
    lock.withLock {
      if (state == State.OPEN) {
        contentHashEnumerator!!.force()
      }
    }
  }

  fun createHashEnumeratorHandle(requestorIndexId: ID<*, *>): HashEnumeratorHandle {
    val handle = HashEnumeratorHandleImpl(requestorIndexId)
    lock.withLock {
      handles.add(handle)
    }
    return handle
  }
}

