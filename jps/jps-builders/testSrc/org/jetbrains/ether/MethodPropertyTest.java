// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

public class MethodPropertyTest extends IncrementalTestCase {
  public MethodPropertyTest() {
    super("methodProperties");
  }

  public void testAddThrows() {
    doTest();
  }

  public void testChangeReturnType() {
    doTest();
  }

  public void testChangeMethodRefReturnType() {
    doTest();
  }

  public void testChangeLambdaTargetReturnType() {
    doTest();
  }

  public void testChangeSAMMethodSignature() {
    doTest();
  }

  public void testChangeSAMMethodSignature2() {
    doTest();
  }

  public void testChangeLambdaSAMMethodSignature() {
    doTest();
  }

  public void testChangeReturnType1() {
    doTest();
  }

  public void testChangeSignature() {
    doTest();
  }

  public void testChangeSignature1() {
    doTest();
  }
}
