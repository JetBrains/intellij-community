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

internal class CompilationTaskContext(
  private val label: String,
  debug: List<String>,
  @JvmField val out: Writer,
) {
  private val start = System.currentTimeMillis()
  private var timings: MutableList<String>?
  private var level = -1
  @JvmField
  val isTracing: Boolean

  init {
    timings = if (debug.contains("timings")) mutableListOf() else null
    isTracing = debug.contains("trace")
  }

  /**
   * Print a list of debugging lines.
   *
   * @param header a header string
   * @param lines a list of lines to print out
   * @param prefix a prefix to add to each line
   * @param filterEmpty if empty lines should be discarded or not
   */
  fun printLines(
    header: String,
    lines: Sequence<String>,
    prefix: String = "|  ",
    filterEmpty: Boolean = false,
  ) {
    check(header.isNotEmpty())
    out.appendLine(if (header.endsWith(":")) header else "$header:")
    for (line in lines) {
      if (line.isNotEmpty() || !filterEmpty) {
        out.appendLine("$prefix$line")
      }
    }
    out.appendLine()
  }

  inline fun whenTracing(block: CompilationTaskContext.() -> Unit) {
    if (isTracing) block() else null
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

  /**
   * This method should be called at the end of builder invocation.
   *
   * @param successful true if the task finished successfully.
   */
  fun finalize(successful: Boolean) {
    if (successful) {
      timings?.also {
        printLines(
          "Task timings for $label (total: ${System.currentTimeMillis() - start} ms)",
          it.asSequence(),
        )
      }
    }
  }
}