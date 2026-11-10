// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DefUseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/defUse");
  }

  @Test
  public void testSCR5144() { doTest(); }
  @Test
  public void testSCR6843() { doTest(); }
  @Test
  public void testUnusedVariable() { doTest(); }
  @Test
  public void testUnusedAssignmentInCompound() { doTest(); }
  @Test
  public void testArrayIndexUsages() { doTest(); }
  @Test
  public void testSCR28019() { doTest(); }
  @Test
  public void testSCR40364() { doTest(); }
  @Test
  public void testArrayLength() { doTest(); }
  @Test
  public void testUsedInArrayInitializer() { doTest(); }
  @Test
  public void testHang() { doTest(); }
  @Test
  public void testOperatorAssignment() { doTest(); }
  @Test
  public void testTryWithFinally() { doTest(); }
  @Test
  public void testTryWithoutFinally() { doTest(); }

  @Test
  public void testSequence() { doTest(); }
  @Test
  public void testIfAfter() { doTest(); }
  @Test
  public void testIfBefore() { doTest(); }
  @Test
  public void testIfNested() { doTest(); }
  @Test
  public void testInLoop() { doTest(); }
  @Test
  public void testIfInLoop() { doTest(); }
  @Test
  public void testThrowInTry() { doTest(); }
  @Test
  public void testThrowInFinally() { doTest(); }
  @Test
  public void testTryThrowFinally() { doTest(); }
  @Test
  public void testNestedTryFinally() { doTest(); }
  @Test
  public void testNestedBigTryFinally() { doTest(); }
  @Test
  public void testTryWithFinallyRethrow() { doTest(); }
  @Test
  public void testComplexDoubleTryFinally() { doTest(); }
  @Test
  public void testComplexTripleTryFinally() { doTest(); }
  @Test
  public void testComplexQuintipleTryFinally() { doTest(); }
  @Test
  public void testAssignmentsInLambdaBody() { doTest(); }
  @Test
  public void testNestedTryFinallyInEndlessLoop() { doTest(); }
  @Test
  public void testNestedTryFinallyInForLoop() { doTest(); }
  @Test
  public void testFieldInitializer() { doTest(); }
  @Test
  public void testChainedFieldInitializer() { doTest(); }
  @Test
  public void testVarDeclaration() { doTest(); }
  @Test
  public void testSelfAssignment() { doTest(); }
  @Test
  public void testFieldIgnoringRedundantInitializer() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DefUseInspection inspection = new DefUseInspection();
    inspection.REPORT_REDUNDANT_INITIALIZER = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }
  @Test
  public void testFieldInitializerUsedInMethodReference() { doTest(); }
  @Test
  public void testFieldInitializerChainedConstructor() { doTest(); }
  @Test
  public void testUnderAlwaysFalseCondition() { doTest(); }
  @Test
  public void testLastInTry() { doTest(); }
  @Test
  public void testFieldOverwrite() { doTest(); }
  @Test
  public void testFieldOverwriteAfterQualifierUpdate() { doTest(); }
  @Test
  public void testFieldNoOverwrite() { doTest(); }
  @Test
  public void testFieldNoOverwriteEmptyElse() { doTest(); }
  @Test
  public void testAssignmentInCatch() { doTest(); }

  @Test
  public void testFieldCouldBeUsedOutside() { doTest(); }
  @Test
  public void testForEachIterationParameter()  { doTest(); }
  @Test
  public void testPatternVariable()  { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new DefUseInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }
}