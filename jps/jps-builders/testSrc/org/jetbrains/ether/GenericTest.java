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
 * Date: 26.09.11
 */
public class GenericTest extends IncrementalTestCase {
  public GenericTest() throws Exception {
    super("generics");
  }

  public void testAddMethodToBase() throws Exception {
    doTest();
  }

  public void testAddParameterizedMethodToBase() throws Exception {
    doTest();
  }

  public void testChangeBound() throws Exception {
    doTest();
  }

  public void testChangeBound1() throws Exception {
    doTest();
  }

  public void testChangeBoundClass1() throws Exception {
    doTest();
  }

  public void testChangeBoundedClass() throws Exception {
    doTest();
  }

  public void testChangeBoundInterface1() throws Exception {
    doTest();
  }

  public void testChangeExtends() throws Exception {
    doTest();
  }

  public void testChangeExtends1() throws Exception {
    doTest();
  }

  public void testChangeExtends2() throws Exception {
    doTest();
  }

  public void testChangeImplements() throws Exception {
    doTest();
  }

  public void testChangeInterfaceTypeParameter() throws Exception {
    doTest();
  }

  public void testChangeToCovariantMethodInBase() throws Exception {
    doTest();
  }

  public void testChangeToCovariantMethodInBase2() throws Exception {
    doTest();
  }

  /* Not working yet
  public void testChangeToCovariantMethodInBase3() throws Exception {
    doTest();
  }
  */
  public void testChangeVarargSignature() throws Exception {
    doTest();
  }

  public void testChangeVarargSignature1() throws Exception {
    doTest();
  }

  public void testCovariance() throws Exception {
    doTest();
  }

  public void testCovariance1() throws Exception {
    doTest();
  }

  public void testCovariance2() throws Exception {
    doTest();
  }

  public void testCovarianceNoChanges() throws Exception {
    doTest();
  }

  public void testDegenerify() throws Exception {
    doTest();
  }

  public void testDegenerify1() throws Exception {
    doTest();
  }

  public void testFieldTypeChange() throws Exception {
    doTest();
  }

  public void testOverrideAnnotatedAnonymous() throws Exception {
    doTest();
  }

  /* Not working yet */
  public void testOverrideAnnotatedAnonymousNotRecompile() throws Exception {
    doTest();
  }

  public void testOverrideAnnotatedInner() throws Exception {
    doTest();
  }

  public void testParamTypes() throws Exception {
    doTest();
  }

  public void testReturnType() throws Exception {
    doTest();
  }

  public void testArgumentContainment() throws Exception {
    doTest();
  }

  public void testArgumentContainment2() throws Exception {
    doTest();
  }

  public void testArgumentContainment3() throws Exception {
    doTest();
  }
}
