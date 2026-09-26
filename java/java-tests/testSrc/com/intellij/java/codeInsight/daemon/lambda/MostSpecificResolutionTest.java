// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MostSpecificResolutionTest extends LightJavaCodeInsightFixtureTestCase5 {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/mostSpecific";

  @NotNull
  @Override
  protected String getRelativePath() {
    return super.getRelativePath() + BASE_PATH;
  }

  @BeforeEach
  void setUp() {
    getFixture().enableInspections(new UnusedDeclarationInspection());
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

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean warnings) {
    getFixture().testHighlighting(warnings, false, false, getTestName(false) + ".java");
  }
}
