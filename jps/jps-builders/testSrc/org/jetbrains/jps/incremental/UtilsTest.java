// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicInteger;

public class UtilsTest extends TestCase {

  public void testCounterRunnable() {
    final int totalInvocationCount = 42;
    final int skipCount = 5;
    final int expectedInvocations = totalInvocationCount / skipCount;

    final AtomicInteger operationInvocations = new AtomicInteger(0);
    final Runnable operation = Utils.asCountedRunnable(skipCount, operationInvocations::incrementAndGet);
    for (int i = 0; i < totalInvocationCount; i++) {
      operation.run();
    }

    assertEquals(expectedInvocations, operationInvocations.get());
  }
}
