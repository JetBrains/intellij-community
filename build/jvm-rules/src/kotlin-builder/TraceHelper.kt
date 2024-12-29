/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.jetbrains.bazel.jvm.kotlin

import java.io.Writer

internal class TraceHelper(
  @JvmField val isTracing: Boolean,
) {
  private val start = System.currentTimeMillis()
  private var timings: MutableList<String>?
  private var level = -1

  init {
    @Suppress("ConstantConditionIf")
    timings = if (false) mutableListOf() else null
  }

  /**
   * Runs a task and records the timings.
   */
  inline fun <T> execute(
    name: String,
    task: () -> T,
  ): T = if (timings == null) task() else pushTimedTask(name, task)

  private inline fun <T> pushTimedTask(
    name: String,
    task: () -> T,
  ): T {
    level += 1
    val previousTimings = timings
    timings = mutableListOf()
    try {
      val start = System.currentTimeMillis()
      val result = task()
      val stop = System.currentTimeMillis()
      previousTimings!!.add("${"  ".repeat(level)} * $name: ${stop - start} ms")
      previousTimings.addAll(timings!!)
      return result
    }
    finally {
      level -= 1
      timings = previousTimings
    }
  }

  fun printTimings(out: Writer, targetLabel: String) {
    val timings = timings ?: return
    val header = "Task timings for $targetLabel (total: ${System.currentTimeMillis() - start} ms)"
    out.appendLine(if (header.endsWith(":")) header else "$header:")
    for (line in timings) {
      out.appendLine("${"|  "}$line")
    }
    out.appendLine()
  }
}