// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine

import java.util.concurrent.atomic.AtomicInteger


class FileSetProcessingStatistics {

  private val total = AtomicInteger()
  private val processed = AtomicInteger()
  private val valid = AtomicInteger()

  fun fileTraversed() {
    total.incrementAndGet()
  }

  fun fileProcessed(valid: Boolean) {
    processed.incrementAndGet()
    if (valid) {
      this.valid.incrementAndGet()
    }
  }

  fun getTotal(): Int = total.get()

  fun getProcessed(): Int = processed.get()

  fun getValid(): Int = valid.get()

}
