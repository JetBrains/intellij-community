// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

public class GenericTest extends IncrementalTestCase {
  public GenericTest() {
    super("generics");
  }

  public void testAddMethodToBase() {
    doTest();
  }

  public void testAddParameterizedMethodToBase() {
    doTest();
  }

  public void testChangeBound() {
    doTest();
  }

  public void testChangeBound1() {
    doTest();
  }

  public void testChangeBoundClass1() {
    doTest();
  }

  public void testChangeBoundedClass() {
    doTest();
  }

  public void testChangeBoundInterface1() {
    doTest();
  }

  public void testChangeExtends() {
    doTest();
  }

  public void testChangeExtends1() {
    doTest();
  }

  public void testChangeExtends2() {
    doTest();
  }

  public void testChangeImplements() {
    doTest();
  }

  public void testChangeInterfaceTypeParameter() {
    doTest();
  }

  public void testChangeToCovariantMethodInBase() {
    doTest();
  }

  public void testChangeToCovariantMethodInBase2() {
    doTest();
  }

  public void testChangeToCovariantMethodInBase3() {
    // Strictly speaking, recompilation of "IImpl" is not necessary, since all needed bridge methods will be included by the compiler into its base "Mediator" class (see the test's classes).
    // However, at the moment when decision is made, both Mediator and IImpl do not have necessary bridge methods, so the corresponding rule affects both classes.
    // At the moment we assume that it is fine to recompile more classes rather than make the rule to be more complicated.
    doTest();
  }

  public void testChangeToCovariantMethodInBase4() {
    doTest();
  }

  public void testChangeVarargSignature() {
    doTest();
  }

  public void testChangeVarargSignature1() {
    doTest();
  }

  public void testCovariance() {
    doTest();
  }

  public void testCovariance1() {
    doTest();
  }

  public void testCovariance2() {
    doTest();
  }

  public void testCovarianceNoChanges() {
    doTest();
  }

  public void testDegenerify() {
    doTest();
  }

  public void testDegenerify1() {
    doTest();
  }

  public void testFieldTypeChange() {
    doTest();
  }

  public void testOverrideAnnotatedAnonymous() {
    doTest();
  }

  /* Not working yet */
  public void testOverrideAnnotatedAnonymousNotRecompile() {
    doTest();
  }

  public void testOverrideAnnotatedInner() {
    doTest();
  }

  public void testParamTypes() {
    doTest();
  }

  public void testReturnType() {
    doTest();
  }

  public void testArgumentContainment() {
    doTest();
  }

  public void testArgumentContainment2() {
    doTest();
  }

  public void testArgumentContainment3() {
    doTest();
  }
}
