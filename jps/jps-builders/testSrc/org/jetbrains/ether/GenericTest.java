// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class GenericTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of("implicitOverrideMethodSignatureChanged");

  public GenericTest() {
    super("generics");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  @Test
  public void testAddMethodToBase() {
    doTest();
  }

  @Test
  public void testAddParameterizedMethodToBase() {
    doTest();
  }

  @Test
  public void testChangeBound() {
    doTest();
  }

  @Test
  public void testChangeBound1() {
    doTest();
  }

  @Test
  public void testChangeBoundClass1() {
    doTest();
  }

  @Test
  public void testChangeBoundedClass() {
    doTest();
  }

  @Test
  public void testChangeBoundInterface1() {
    doTest();
  }

  @Test
  public void testChangeExtends() {
    doTest();
  }

  @Test
  public void testChangeExtends1() {
    doTest();
  }

  @Test
  public void testChangeExtends2() {
    doTest();
  }

  @Test
  public void testChangeImplements() {
    doTest();
  }

  @Test
  public void testChangeInterfaceTypeParameter() {
    doTest();
  }

  @Test
  public void testChangeToCovariantMethodInBase() {
    doTest();
  }

  @Test
  public void testChangeToCovariantMethodInBase2() {
    doTest();
  }

  @Test
  public void testChangeToCovariantMethodInBase3() {
    // Strictly speaking, recompilation of "IImpl" is not necessary, since all needed bridge methods will be included by the compiler into its base "Mediator" class (see the test's classes).
    // However, at the moment when decision is made, both Mediator and IImpl do not have necessary bridge methods, so the corresponding rule affects both classes.
    // At the moment we assume that it is fine to recompile more classes rather than make the rule to be more complicated.
    doTest();
  }

  @Test
  public void testChangeToCovariantMethodInBase4() {
    doTest();
  }

  @Test
  public void testChangeVarargSignature() {
    doTest();
  }

  @Test
  public void testChangeVarargSignature1() {
    doTest();
  }

  @Test
  public void testCovariance() {
    doTest();
  }

  @Test
  public void testCovariance1() {
    doTest();
  }

  @Test
  public void testCovariance2() {
    doTest();
  }

  @Test
  public void testCovarianceNoChanges() {
    doTest();
  }

  @Test
  public void testDegenerify() {
    doTest();
  }

  @Test
  public void testDegenerify1() {
    doTest();
  }

  @Test
  public void testFieldTypeChange() {
    doTest();
  }

  @Test
  public void testOverrideAnnotatedAnonymous() {
    doTest();
  }

  /* Not working yet */
  @Test
  public void testOverrideAnnotatedAnonymousNotRecompile() {
    doTest();
  }

  @Test
  public void testOverrideAnnotatedInner() {
    doTest();
  }

  @Test
  public void testParamTypes() {
    doTest();
  }

  @Test
  public void testReturnType() {
    doTest();
  }

  @Test
  public void testArgumentContainment() {
    doTest();
  }

  @Test
  public void testArgumentContainment2() {
    doTest();
  }

  @Test
  public void testArgumentContainment3() {
    doTest();
  }

  @Test
  public void testImplicitOverrideMethodSignatureChanged() {
    doTest().assertFailed();
  }
}
