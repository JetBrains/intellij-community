// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service
class TemporaryStringEnumerator {
  private val repr2id = mutableMapOf<String, Int>()
  private val id2Repr = ArrayList<String>(2_000_000)
  private val mutex = Mutex()

  suspend fun enumerate(value: String): Int = mutex.withLock {
    repr2id.getOrPut(value) {
      id2Repr.add(value)
      id2Repr.size - 1
    }
  }

  suspend fun valueOf(index: Int): String = mutex.withLock { id2Repr[index] }

  companion object {
    fun getInstance(): TemporaryStringEnumerator = service()
  }
}
