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

public class Diamond8HighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/diamond";

  public void testAvoidClassRefCachingDuringInference() {
    doTest();
  }

  public void testIDEA97294() {
    doTest();
  }

  public void testOuterClass() {
    doTest();
  }

  public void testVarargs() {
    doTest();
  }

  public void testOverloadOuterCall() {
    doTest();
  }

  public void testWithConstructorRefInside() {
    doTest();
  }
  
  public void testIDEA140686() {
    doTest();
  }

  public void testDiagnosticMessageWhenConstructorIsUnresolved() {
    doTest();
  }

  public void testNullTypesInDiamondsInference() {
    doTest();
  }

  public void testNoRawTypeInferenceWhenNewExpressionHasSpecifiedType() {
    doTest();
  }

  public void testEraseTypeForNewExpressionWithDiamondsIfUncheckedConversionWasPerformedDuringApplicabilityCheck() {
    doTest();
  }

  public void testEnsureApplicabilityForDiamondCallIsCheckedBasedOnStaticFactoryApplicability() {
    doTest();
  }

  public void testConflictingNamesInConstructorAndClassTypeParameters() {
    doTest();
  }

  public void testParameterizedConstructorWithDiamonds() {
    doTest();
  }

  public void testRawArgumentInsideNewExpression() {
    doTest();
  }

  public void testDiamondConstructorWithTypeParameters() {
    doTest();
  }

  public void testRawTypePassedToDiamond() {
    doTest();
  }

  public void testExceptionsThrownFromConstructorShouldBePreserved() {
    doTest();
  }

  public void testDiamondInsideOverloadedThisReference() {
    doTest();
  }

  public void testDetectStaticFactoryForTopLevelCall() {
    doTest();
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}
