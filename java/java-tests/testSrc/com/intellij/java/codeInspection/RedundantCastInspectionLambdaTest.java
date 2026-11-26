// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.idea.TestFor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class RedundantCastInspectionLambdaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk21()));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture, () -> List.of(new RedundantCastInspection()));

  private static final String BASE_PATH = "/inspection/redundantCast/lambda/";

  private void doTest() {
    daemonFixture.get().doTest(BASE_PATH + daemonFixture.get().getFixture().getTestName(false) + ".java", true, false);
  }

  @Test public void testLambdaContext() { doTest(); }
  @Test public void testMethodRefContext() { doTest(); }
  @Test public void testExpectedSupertype() { doTest(); }
  @Test public void testExpressionRendering() { doTest(); }
  @Test public void testForeachValue() { doTest(); }
  @Test public void testConditional() { doTest(); }
  @Test public void testInferApplicabilityError() { doTest(); }
  @Test public void testCastToRawType() { doTest(); }
  @Test public void testEnumConstantArgument() { doTest(); }
  @Test public void testCastInMultidimensionalArrayIndex() { doTest(); }
  @Test public void testCastWithClassHierarchyWithPrivateMethods() { doTest(); }
  @Test public void testFieldInitializer() { doTest(); }
  @Test public void testDiamondWithUpperBounds() { doTest(); }
  @Test public void testBinaryConversions() { doTest(); }
  @Test public void testInferenceIncompatibilityWithoutCast() { doTest(); }
  @Test public void testCastToPrimitive() { doTest(); }
  @Test public void testParenthesisAroundConditional() { doTest(); }
  @Test public void testMiscStatements() { doTest(); }
  @Test public void testSameSubstitutor() { doTest(); }
  @Test public void testSameUpperBounds() { doTest(); }
  @Test public void testSameResolveWithConditionalBranches() { doTest(); }
  @Test public void testIgnoreMessageFormatCall() { doTest(); }
  @Test public void testClassCastMethodReference() { doTest(); }

  @TestFor(issues = "IDEA-361212")
  @Test public void testNestedCallRawInheritance() {doTest();}

  @TestFor(issues = {"IDEA-375458", "IDEA-377186"})
  @Test public void testExplicitNullabilityAnnotation() { doTest(); }

  @TestFor(issues = "IDEA-381105")
  @Test public void testLambdaParameterFieldAccess() { doTest(); }

  @TestFor(issues = "IDEA-381105")
  @Test public void testLambdaParameterFieldAccessSimple() { doTest(); }
}