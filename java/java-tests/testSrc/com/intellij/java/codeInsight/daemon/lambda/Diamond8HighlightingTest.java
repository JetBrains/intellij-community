// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public void testConstructorAccessibility() {
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

  public void testOverloadedConstructorsUnresolvedWithoutDiamonds() {
    doTest();
  }

  public void testNestedDiamondsInsideAssignmentInMethodsCall() { doTest();}

  public void testNestedDiamondsAnonymousCase() { doTest();}

  public void testDiamondsUncheckedWarning() { doTest();}
  public void testWrongNumberOfArguments() { doTest();}

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}
