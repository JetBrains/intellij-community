// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class Diamond8HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/diamond";

  @Test
  public void testAvoidClassRefCachingDuringInference() {
    doTest();
  }

  @Test
  public void testIDEA97294() {
    doTest();
  }

  @Test
  public void testConstructorAccessibility() {
    doTest();
  }

  @Test
  public void testOuterClass() {
    doTest();
  }

  @Test
  public void testVarargs() {
    doTest();
  }

  @Test
  public void testOverloadOuterCall() {
    doTest();
  }

  @Test
  public void testWithConstructorRefInside() {
    doTest();
  }
  
  @Test
  public void testIDEA140686() {
    doTest();
  }

  @Test
  public void testDiagnosticMessageWhenConstructorIsUnresolved() {
    doTest();
  }

  @Test
  public void testNullTypesInDiamondsInference() {
    doTest();
  }

  @Test
  public void testNoRawTypeInferenceWhenNewExpressionHasSpecifiedType() {
    doTest();
  }

  @Test
  public void testEraseTypeForNewExpressionWithDiamondsIfUncheckedConversionWasPerformedDuringApplicabilityCheck() {
    doTest();
  }

  @Test
  public void testEnsureApplicabilityForDiamondCallIsCheckedBasedOnStaticFactoryApplicability() {
    doTest();
  }

  @Test
  public void testConflictingNamesInConstructorAndClassTypeParameters() {
    doTest();
  }

  @Test
  public void testParameterizedConstructorWithDiamonds() {
    doTest();
  }

  @Test
  public void testRawArgumentInsideNewExpression() {
    doTest();
  }

  @Test
  public void testDiamondConstructorWithTypeParameters() {
    doTest();
  }

  @Test
  public void testRawTypePassedToDiamond() {
    doTest();
  }

  @Test
  public void testExceptionsThrownFromConstructorShouldBePreserved() {
    doTest();
  }

  @Test
  public void testDiamondInsideOverloadedThisReference() {
    doTest();
  }

  @Test
  public void testDetectStaticFactoryForTopLevelCall() {
    doTest();
  }

  @Test
  public void testOverloadedConstructorsUnresolvedWithoutDiamonds() {
    doTest();
  }

  @Test public void testNestedDiamondsInsideAssignmentInMethodsCall() { doTest();}

  @Test public void testNestedDiamondsAnonymousCase() { doTest();}

  @Test public void testDiamondsUncheckedWarning() { doTest();}
  @Test public void testWrongNumberOfArguments() { doTest();}
  @Test
  public void testNestedClassArgConstructor() {
    doTest();
  }

  private void doTest() {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", false, false);
  }
}
