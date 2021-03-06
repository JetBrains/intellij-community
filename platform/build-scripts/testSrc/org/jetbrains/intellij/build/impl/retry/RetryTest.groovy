// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.retry

import org.jetbrains.intellij.build.BuildMessages
import org.junit.Test

class RetryTest {
  private def log = [
    info : { println it.toString() },
    error: { String message -> throw new Exception(message) }
  ] as BuildMessages
  private def retries = 10
  private def retry = new Retry(log, retries, 1)

  @Test
  void 'retry test'() {
    assert retry.call { 42 } == 42
    def attempts = 0
    assert retry.call {
      if (it > retries / 2) return 42
      attempts++
      throw new Exception("${it}th failure")
    } == 42
    assert attempts > 0
  }
}