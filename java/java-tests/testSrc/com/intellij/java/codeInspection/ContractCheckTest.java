// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.ContractInspection;
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
public class ContractCheckTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/contractCheck/");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new ContractInspection());
    myFixture.testHighlighting(true, false, true, myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testTrueInsteadOfFalse() { doTest(); }
  @Test
  public void testTrueInsteadOfFail() { doTest(); }
  @Test
  public void testWrongFail() { doTest(); }
  @Test
  public void testNotNullStringLiteral() { doTest(); }
  @Test
  public void testPlainDelegation() { doTest(); }
  @Test
  public void testDelegationToInstanceMethod() { doTest(); }
  @Test
  public void testFailDelegation() { doTest(); }
  @Test
  public void testDelegationWithUnknownArgument() { doTest(); }
  @Test
  public void testEqualsUnknownValue() { doTest(); }
  @Test
  public void testMissingFail() { doTest(); }
  @Test
  public void testInheritedContract() { doTest(); }
  @Test
  public void testExceptionWhenDeclaredNotNull() { doTest(); }
  @Test
  public void testCheckSuperContract() { doTest(); }
  @Test
  public void testNestedCallsMayThrow() { doTest(); }

  @Test
  public void testSignatureIssues() { doTest(); }
  @Test
  public void testVarargInferred() { doTest(); }
  @Test
  public void testDoubleParameter() { doTest(); }
  @Test
  public void testReturnPrimitiveArray() { doTest(); }
  @Test
  public void testCheckConstructorContracts() { doTest(); }

  @Test
  public void testPassingVarargsToDelegate() { doTest(); }
  @Test
  public void testUnknownIfCondition() { doTest(); }
  @Test
  public void testCallingNotNullMethod() { doTest(); }
  @Test
  public void testMutationSignatureProblems() { doTest(); }
  @Test
  public void testNewThisParam() { doTest(); }
  @Test
  public void testConditionsConflict() { doTest(); }
  @Test
  public void testWrongFailSuggestion() { doTest(); }
  @Test
  public void testIsInstance() { doTest(); }
  @Test
  public void testObjectBoolean() { doTest(); }
  @Test
  public void testParamUncheckedCast() { doTest(); }
  @Test
  public void testInferredNotNull() { doTest(); }
}
