// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine;

import java.util.concurrent.atomic.AtomicInteger;


public class FileSetProcessingStatistics {

  private final AtomicInteger total = new AtomicInteger();
  private final AtomicInteger processed = new AtomicInteger();
  private final AtomicInteger valid = new AtomicInteger();

  public void fileTraversed() {
    total.incrementAndGet();
  }

  public void fileProcessed(boolean valid) {
    processed.incrementAndGet();
    if (valid) {
      this.valid.incrementAndGet();
    }
  }

  public int getTotal() {
    return total.get();
  }

  public int getProcessed() {
    return processed.get();
  }

  public int getValid() {
    return valid.get();
  }
}
