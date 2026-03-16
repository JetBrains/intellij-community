// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class FlowWithHistoryTest {
  @Test
  fun testConsistentHistory() = runBlocking(Dispatchers.Default) {
    val flow = TestFlow(this)
    val count = 10000
    launch {
      for (i in 0..<count) {
        flow.emit(i)
      }
    }
    repeat (100) {
      launch {
        flow.getFlowWithHistory().take(count).collectIndexed { index, value ->
          if (value != index) {
            throw RuntimeException("Unexpected value at index $index: $value")
          }
        }
      }
    }
  }
}

private class TestFlow(scope: CoroutineScope) : FlowWithHistory<Int>(scope) {
  private val history = mutableListOf<Int>()

  override fun getHistory() = history.toList()

  fun emit(value : Int) = updateHistoryAndEmit {
    history.add(value)
    value
  }
}