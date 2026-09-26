// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The run's infrastructure diagnostics (raw browser process output, static-server misses):
 * append-only and line-oriented. The browser output listener and the static server write
 * concurrently from their own threads, so thread safety lives here — writers just call
 * [appendLine] — and [toString] renders the lines in append order (the interleaving is part
 * of the diagnostic value); the harness only reads once the run's components are joined.
 */
class InfrastructureLog {
  private val lines = ConcurrentLinkedQueue<String>()

  fun appendLine(line: String) {
    lines.add(line)
  }

  override fun toString(): String = lines.joinToString(separator = "") { line -> "$line\n" }
}
