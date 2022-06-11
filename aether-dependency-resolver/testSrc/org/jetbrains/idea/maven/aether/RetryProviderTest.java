// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jetbrains.idea.maven.aether.RetryProvider.disabled;
import static org.jetbrains.idea.maven.aether.RetryProvider.withExponentialBackOff;
import static org.junit.jupiter.api.Assertions.*;

class RetryProviderTest {
  private final Logger logger = LoggerFactory.getLogger(RetryProviderTest.class);
  private final Retry retryDisabled = disabled();
  private final Retry retryWithExpBackOff = withExponentialBackOff(1000, 5000, 5);

  @Test
  public void disabled_testIsSingleton() {
    Retry first = disabled();
    Retry second = disabled();
    assertSame(first, second);
  }

  @Test
  public void disabled_testReturnsCorrectValue() throws Exception {
    int expected = 42;
    int actual = retryDisabled.retry(() -> expected, logger);
    assertEquals(expected, actual);
  }

  @Test
  public void disabled_testCanBeReused() throws Exception {
    int expected = 42;
    int ignored = retryDisabled.retry(() -> expected, logger);
    int actual = retryDisabled.retry(() -> expected, logger);
    assertEquals(expected, actual);
  }

  @Test
  public void disabled_testRethrowsException() {
    String expected = "Value42";
    assertThrows(Exception.class, () -> retryDisabled.retry(() -> {
      throw new Exception(expected);
    }, logger), expected);
  }

  @Test
  public void expBackOff_testThrowsOnIllegalArguments() {
    assertThrows(IllegalArgumentException.class, () -> withExponentialBackOff(0, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> withExponentialBackOff(1, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> withExponentialBackOff(1, 1, 0));
  }

  @Test
  public void expBackOff_testOneAttempt() throws Exception {
    int expected = 42;
    int actual = retryWithExpBackOff.retry(() -> expected, logger);
    assertEquals(expected, actual);
  }

  @Test
  public void expBackOff_testRetry() throws Exception {
    int attempts = retryWithExpBackOff.retry(new ThrowingSupplier<Integer>() {
      private int attempts = 0;

      @Override
      public Integer get() throws Exception {
        if (attempts == 0) {
          /* Simulate a fail */
          ++attempts;
          throw new Exception();
        }

        return attempts;
      }
    }, logger);

    assertTrue(attempts > 0, "attempts > 0");
  }

  @Test
  public void expBackOff_testCanBeReused() throws Exception {
    int expected = 42;
    int ignored = retryWithExpBackOff.retry(() -> expected, logger);
    int actual = retryWithExpBackOff.retry(() -> expected, logger);
    assertEquals(expected, actual);
  }

  @Test
  public void expBackOff_testRethrowsException() {
    String expected = "Value42";
    assertThrows(Exception.class, () -> retryWithExpBackOff.retry(() -> {
      throw new Exception(expected);
    }, logger), expected);
  }
}