// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import kotlin.coroutines.cancellation.CancellationException

/**
 * Not thread safe. Avoid using it in multi-thread environment.
 *
 * CharSequence that throws cancelled exception when parent job is cancelled.
 * Useful for performing blocking calls on CharSequences, e.g. [java.util.regex.Pattern.matcher].
 *
 * As it may throw CancelledException, you must not use it in a Job that is not inherited from the passed one.
 * Otherwise, it might be unexpectedly cancelled.
 */
class BombedCharSequence(private val job: () -> Boolean, private val delegate: CharSequence) : CharSequence {
  private var i = 0

  private fun check() {
    if (++i and 1023 == 0) {
      if (!job()) {
        throw CancellationException()
      }
    }
  }

  override val length: Int
    get() {
      check()
      return delegate.length
    }

  override fun get(index: Int): Char {
    check()
    return delegate[index]
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
    check()
    return delegate.subSequence(startIndex, endIndex)
  }
}
