// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addCheckerAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowInspection16Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_16);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testInstanceOfPattern() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchStatements() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchStatementUnreachableBranches() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchExpressions() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchExpressionsNullability() { dataFlow.get().doTest(); }
  @Test
  public void testConstantDescAsWrapperSupertype() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package java.lang.constant; public interface ConstantDesc {}");
    dataFlow.get().doTest();
  }
  @Test
  public void testSwitchExpressionAndLambdaInlining() { dataFlow.get().doTest(); }
  @Test
  public void testRecordAccessorStability() { dataFlow.get().doTest(); }
  @Test
  public void testRecordAccessorContainerAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, METHOD}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NonnullByDefault {}");
    dataFlow.get().doTest();
  }
  @Test
  public void testStaticFieldInAnonymous() { dataFlow.get().doTest(); }

  @Test
  public void testAccessorNullityUnderDefaultQualifier() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addCheckerAnnotations(myFixture);
    dataFlow.get().doTest();
  }
  
  @Test
  public void testCheckerLocalVariableOverridesDefault() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addCheckerAnnotations(myFixture);
    dataFlow.get().doTest();
  }
  
  @Test
  public void testRecordComponentAnnotate() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    dataFlow.get().doTest();
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction intention = myFixture.findSingleIntention("Annotate record component 'member' as '@Nullable'");
      myFixture.checkPreviewAndLaunchAction(intention);
      myFixture.checkResultByFile(dataFlow.get().getTestName(false) + "_after.java");
    });
  }
}