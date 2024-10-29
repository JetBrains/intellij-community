/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.util

import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.util.*

@ApiStatus.Internal
class TruncatingPrintBuffer(
  private val headLimit: Int,
  private val tailLimit: Int,
  private val printFunc: (String) -> Any
) : Closeable {
  private val queue: Queue<String> = LinkedList()
  private var linesPrinted = 0
  private var linesLost = 0
  private var closed = false

  override fun close() {
    if (closed) return
    closed = true

    if (linesLost > 0) {
      while (queue.size > tailLimit) {
        queue.remove()
        linesLost++
      }
      assert(linesLost > 1)
      printFunc("[...removed $linesLost lines...]")
    }
    queue.forEach {
      printFunc(it)
    }
    queue.clear()
  }

  fun println() {
    println("")
  }

  fun println(s: String) {
    if (closed) throw IllegalStateException()
    if (linesPrinted < headLimit) {
      printFunc(s)
      linesPrinted++
    }
    else {
      queue.add(s)
      if (queue.size > tailLimit + 1) {
        queue.remove()
        linesLost++
      }
    }
  }
}
