// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Suppressions")
package com.intellij.util

/**
 * Runs given code blocks sequentially, catching exceptions along the way.
 * The first thrown exception (if any) is propagated.
 * Further exceptions (again, if any) are added to the "suppressed" list of the first one.
 */
fun runSuppressing(vararg blocks: () -> Unit) {
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

/** A Java-friendly overload of [runSuppressing]. */
fun runSuppressing(vararg runnables: ThrowableRunnable<Throwable>) {
  var first: Throwable? = null

  for (runnable in runnables) {
    try {
      runnable.run()
    }
    catch (t: Throwable) {
      first = addSuppressed(first, t)
    }
  }

  if (first != null) {
    throw first
  }
}

fun <T: Throwable> addSuppressed(first: T?, next: T): T {
  if (first != null) {
    first.addSuppressed(next)
    return first
  }
  else {
    return next
  }
}
