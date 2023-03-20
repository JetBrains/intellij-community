// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class DefUseTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/defUse";
  }

  public void testSCR5144() { doTest(); }
  public void testSCR6843() { doTest(); }
  public void testUnusedVariable() { doTest(); }
  public void testUnusedAssignmentInCompound() { doTest(); }
  public void testArrayIndexUsages() { doTest(); }
  public void testSCR28019() { doTest(); }
  public void testSCR40364() { doTest(); }
  public void testArrayLength() { doTest(); }
  public void testUsedInArrayInitializer() { doTest(); }
  public void testHang() { doTest(); }
  public void testOperatorAssignment() { doTest(); }
  public void testTryWithFinally() { doTest(); }
  public void testTryWithoutFinally() { doTest(); }

  public void testSequence() { doTest(); }
  public void testIfAfter() { doTest(); }
  public void testIfBefore() { doTest(); }
  public void testIfNested() { doTest(); }
  public void testInLoop() { doTest(); }
  public void testIfInLoop() { doTest(); }
  public void testThrowInTry() { doTest(); }
  public void testThrowInFinally() { doTest(); }
  public void testTryThrowFinally() { doTest(); }
  public void testNestedTryFinally() { doTest(); }
  public void testNestedBigTryFinally() { doTest(); }
  public void testTryWithFinallyRethrow() { doTest(); }
  public void testComplexDoubleTryFinally() { doTest(); }
  public void testComplexTripleTryFinally() { doTest(); }
  public void testComplexQuintipleTryFinally() { doTest(); }
  public void testAssignmentsInLambdaBody() { doTest(); }
  public void testNestedTryFinallyInEndlessLoop() { doTest(); }
  public void testNestedTryFinallyInForLoop() { doTest(); }
  public void testFieldInitializer() { doTest(); }
  public void testChainedFieldInitializer() { doTest(); }
  public void testVarDeclaration() { doTest(); }
  public void testSelfAssignment() { doTest(); }
  public void testFieldIgnoringRedundantInitializer() {
    DefUseInspection inspection = new DefUseInspection();
    inspection.REPORT_REDUNDANT_INITIALIZER = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
  public void testFieldInitializerUsedInMethodReference() { doTest(); }
  public void testFieldInitializerChainedConstructor() { doTest(); }
  public void testUnderAlwaysFalseCondition() { doTest(); }
  public void testLastInTry() { doTest(); }
  public void testFieldOverwrite() { doTest(); }
  public void testFieldOverwriteAfterQualifierUpdate() { doTest(); }
  public void testAssignmentInCatch() { doTest(); }

  public void testFieldCouldBeUsedOutside() { doTest(); }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  private void doTest() {
    myFixture.enableInspections(new DefUseInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}