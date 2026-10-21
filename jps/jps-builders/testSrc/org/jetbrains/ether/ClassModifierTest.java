// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.junit.jupiter.api.Test;

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

  @Test
  public void testAddStatic() {
    doTest();
  }

  @Test
  public void testRemoveStatic() {
    doTest();
  }

  @Test
  public void testDecAccess() {
    doTest();
  }

  @Test
  public void testSetAbstract() {
    doTest();
  }

  @Test
  public void testDropAbstract() {
    doTest();
  }

  @Test
  public void testSetFinal() {
    doTest();
  }

  @Test
  public void testSetFinal1() {
    doTest();
  }

  @Test
  public void testBecameSealed() {
    doTest().assertFailed();
  }

  @Test
  public void testChangeInnerClassModifiers() {
    doTest();
  }
}
