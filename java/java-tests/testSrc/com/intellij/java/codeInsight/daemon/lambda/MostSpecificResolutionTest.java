// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
class MostSpecificResolutionTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/mostSpecific";

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + BASE_PATH);
    codeInsightFixture.get().enableInspections(new UnusedDeclarationInspection());
  }

  @Test
  void testVoidConflict() {
    doTest();
  }

  @Test
  void testNestedLambdaSpecifics() {
    doTest();
  }

  @Test
  void testBothVarargs() {
    doTest();
  }

  @Test
  void testNestedVarargs() {
    doTest();
  }

  @Test
  void testMostSpecificForSameFunctionalTypes() {
    doTest();
  }

  @Test
  void testIDEA121884() {
    doTest();
  }

  @Test
  void testIDEA121999() {
    doTest();
  }

  @Test
  void testRelatedSAMErasures() {
    doTest();
  }

  @Test
  void testJDK8034223() {
    doTest();
  }

  @Test
  void testIDEA123352() {
    doTest();
  }

  @Test
  void testIncompleteMethodInInterface() {
    doTest(false);
  }

  @Test
  void testMostSpecificByReturnType() {
    doTest();
  }

  @Test
  void testDifferentParamsLength() {
    doTest(false);
  }

  @Test
  void testNoReturnTypeResolutionForThrownException() {
    doTest(false);
  }

  @Test
  void testBoxingAndOverloadResolution() {
    doTest();
  }

  @Test
  void testSuperMethodsInExactCheck() {
    doTest();
  }

  @Test
  void testTargetTypeParameter() {
    doTest(false);
  }

  @Test
  void testJDK8042508() {
    if (Registry.is("JDK8042508.bug.fixed", false)) {
      doTest(false);
    }
  }

  @Test
  void testIDEA125855() {
    doTest();
  }

  @Test
  void testIDEA127584() {
    doTest();
  }

  @Test
  void testVarargsSpecificsDuringMethodReferenceResolve() {
    doTest();
  }

  @Test
  void testFunctionalTypeComparisonWhenMethodsAreNotGeneric() {
    doTest(false);
  }

  @Test
  void testInferSpecificForGenericMethodWhenCallProvidesExplicitTypeArguments() {
    doTest(false);
  }

  @Test
  void testIncompatibleSiteSubstitutionBounds() {
    doTest(false);
  }

  @Test
  void testEnsureArgTypesAreNotCalculatedDuringOverload() {
    doTest();
  }

  @Test
  void testInaccessibleConstructorInExactMethodReference() {
    doTest(false);
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean warnings) {
    codeInsightFixture.get().testHighlighting(warnings, false, false, codeInsightFixture.get().getTestName(false) + ".java");
  }
}
