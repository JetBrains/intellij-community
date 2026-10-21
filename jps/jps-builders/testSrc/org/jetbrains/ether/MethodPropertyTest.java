// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.junit.jupiter.api.Test;

public class MethodPropertyTest extends IncrementalTestCase {
  public MethodPropertyTest() {
    super("methodProperties");
  }

  @Test
  public void testAddThrows() {
    doTest();
  }

  @Test
  public void testChangeReturnType() {
    doTest();
  }

  @Test
  public void testChangeMethodRefReturnType() {
    doTest();
  }

  @Test
  public void testChangeLambdaTargetReturnType() {
    doTest();
  }

  @Test
  public void testChangeSAMMethodSignature() {
    doTest();
  }

  @Test
  public void testChangeSAMMethodSignature2() {
    doTest();
  }

  @Test
  public void testChangeLambdaSAMMethodSignature() {
    doTest();
  }

  @Test
  public void testChangeReturnType1() {
    doTest();
  }

  @Test
  public void testChangeSignature() {
    doTest();
  }

  @Test
  public void testChangeSignature1() {
    doTest();
  }
}
