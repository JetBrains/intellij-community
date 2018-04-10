/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.ether;

/**
 * @author: db
 */
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

  /* Not working yet
  public void testChangeToCovariantMethodInBase3() throws Exception {
    doTest();
  }
  */
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
