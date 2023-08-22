// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Suppressions")

package com.intellij.util

/**
 * Sequentially runs given code blocks, catching exceptions along the way.
 * The first thrown exception (if any) is propagated.
 * Subsequent exceptions (again, if any) are added to the "suppressed" list of the first one.
 */
fun runSuppressing(vararg blocks: () -> Unit): Unit =
  runSuppressing(blocks.asSequence())

/** A Java-friendly overload of [runSuppressing]. */
fun runSuppressing(vararg runnables: ThrowableRunnable<Throwable>): Unit =
  runSuppressing(runnables.asSequence().map { r -> { r.run() } })

private fun runSuppressing(blocks: Sequence<() -> Unit>) {
  var first: Throwable? = null

  for (block in blocks) {
    try {
      block()
    }
    catch (t: Throwable) {
      first = addSuppressed(first, t)
    }
  }

  if (first != null) {
    throw first
  }
}

fun <T: Throwable> addSuppressed(first: T?, next: T): T =
  first?.also { it.addSuppressed(next) } ?: next
