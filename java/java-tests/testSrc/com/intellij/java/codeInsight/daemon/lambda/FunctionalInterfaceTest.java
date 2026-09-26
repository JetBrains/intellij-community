/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import org.jetbrains.annotations.NonNls;

public class FunctionalInterfaceTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/functionalInterface";

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }

  public void testSimple() {
    doTest();
  }

  public void testNoMethods() {
    doTest();
  }

  public void testMultipleMethods() {
    doTest();
  }
  
  public void testMultipleMethodsInOne() {
    doTest();
  }

  public void testIntersectionOf2FunctionalTypesWithEqualSignatures() {
    doTest();
  }

  public void testIntersectionOf2FunctionalTypesWithEqualAfterSubstitutionSignatures() {
    doTest();
  }

  public void testClone() {
    doTest();
  }

  public void testTwoMethodsSameSignature() {
    doTest();
  } 
  
  public void testTwoMethodsSubSignature() {
    doTest();
  }
  
  public void testTwoMethodsNoSubSignature() {
    doTest();
  }
  
  public void testTwoMethodsNoSubSignature1() {
    doTest();
  } 
  
  public void testTwoMethodsSameSubstSignature() {
    doTest();
  }
  
  public void testMethodWithTypeParam() {
    doTest();
  }
  
  public void testTwoMethodsSameSignatureTypeParams() {
    doTest();
  }

  public void testAbstractClass() {
    doTest();
  }

  public void testIntersectionTypeWithSameBaseInterfaceInConjuncts() {
    doTest();
  }
}
