// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ether;

/**
 * @author: db
 */
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
