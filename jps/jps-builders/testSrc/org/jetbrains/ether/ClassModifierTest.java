// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;

import java.util.Set;

public class ClassModifierTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of("becameSealed");

  public ClassModifierTest() {
    super("classModifiers");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  public void testAddStatic() {
    doTest();
  }

  public void testRemoveStatic() {
    doTest();
  }

  public void testDecAccess() {
    doTest();
  }

  public void testSetAbstract() {
    doTest();
  }

  public void testDropAbstract() {
    doTest();
  }

  public void testSetFinal() {
    doTest();
  }

  public void testSetFinal1() {
    doTest();
  }

  public void testBecameSealed() {
    doTest().assertFailed();
  }

  public void testChangeInnerClassModifiers() {
    doTest();
  }
}
