// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
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
public class RedundantCastInspectionGenericsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantCast/generics");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new RedundantCastInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testBoxingInRef() { doTest(); }

  @Test
  public void testBoxingInConditional() { doTest(); }

  @Test
  public void testInference1() { doTest(); }

  @Test
  public void testInference2() { doTest(); }

  @Test
  public void testInference3() { doTest(); }

  @Test
  public void testNullInVarargsParameter() { doTest(); }
  @Test
  public void testSingleArgForVarargsParameter() { doTest(); }
  @Test
  public void testSwitchSelector() { doTest(); }

  @Test
  public void testWrapperToPrimitiveCast() { doTest(); }

  @Test
  public void testEnumConstant() { doTest(); }

  @Test
  public void testRawCast() { doTest(); }
  @Test
  public void testCastToUnboundWildcard() { doTest(); }

  @Test
  public void testCastCast() { doTest(); }
  @Test
  public void testReturnValue() { doTest(); }

  @Test
  public void testIDEA22899() { doTest(); }
  @Test
  public void testRawCast1() { doTest(); }
  @Test
  public void testInferenceFromCast() { doTest(); }
  @Test
  public void testGetClassProcessing() { doTest(); }
  @Test
  public void testInstanceOfChecks() { doTest(); }
  @Test
  public void testForEachValue() { doTest(); }
  @Test
  public void testForEachValueIDEA126166() { doTest(); }
  @Test
  public void testCaseThrowable() { doTest(); }
  @Test
  public void testSafeTempVarName() { doTest(); }
  @Test
  public void testBinaryComparison() { doTest(); }
  @Test
  public void testQualifierWithCapture() { doTest(); }

  @Test
  public void testTypeParameterAccessChecksJava7() {
    doTest();
  }

  @Test
  public void testBoxingTopCast() {
    doTest();
  }

  @Test
  public void testIgnore() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final RedundantCastInspection castInspection = new RedundantCastInspection();
    castInspection.IGNORE_SUSPICIOUS_METHOD_CALLS = true;
    myFixture.enableInspections(castInspection);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testIgnoreSuspicious() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final RedundantCastInspection castInspection = new RedundantCastInspection();
    castInspection.IGNORE_SUSPICIOUS_METHOD_CALLS = true;
    myFixture.enableInspections(castInspection);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testDifferentNullness() { doTest(); }
  @Test
  public void testSuspiciousVarargsCall() { doTest(); }

  @Test
  public void testPrimitiveWidening() { doTest(); }
  @Test
  public void testCastLongLiteral() { doTest(); }
}