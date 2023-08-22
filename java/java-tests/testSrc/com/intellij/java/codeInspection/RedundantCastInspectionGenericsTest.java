// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class RedundantCastInspectionGenericsTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/redundantCast/generics";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  private void doTest() {
    myFixture.enableInspections(new RedundantCastInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testBoxingInRef() { doTest(); }

  public void testBoxingInConditional() { doTest(); }

  public void testInference1() { doTest(); }

  public void testInference2() { doTest(); }

  public void testInference3() { doTest(); }

  public void testNullInVarargsParameter() { doTest(); }
  public void testSingleArgForVarargsParameter() { doTest(); }
  public void testSwitchSelector() { doTest(); }

  public void testWrapperToPrimitiveCast() { doTest(); }

  public void testEnumConstant() { doTest(); }

  public void testRawCast() { doTest(); }
  public void testCastToUnboundWildcard() { doTest(); }

  public void testCastCast() { doTest(); }
  public void testReturnValue() { doTest(); }

  public void testIDEA22899() { doTest(); }
  public void testRawCast1() { doTest(); }
  public void testInferenceFromCast() { doTest(); }
  public void testGetClassProcessing() { doTest(); }
  public void testInstanceOfChecks() { doTest(); }
  public void testForEachValue() { doTest(); }
  public void testForEachValueIDEA126166() { doTest(); }
  public void testCaseThrowable() { doTest(); }
  public void testSafeTempVarName() { doTest(); }
  public void testBinaryComparison() { doTest(); }
  public void testQualifierWithCapture() { doTest(); }

  public void testTypeParameterAccessChecksJava7() {
    doTest();
  }

  public void testBoxingTopCast() {
    doTest();
  }

  public void testIgnore() {
    final RedundantCastInspection castInspection = new RedundantCastInspection();
    castInspection.IGNORE_SUSPICIOUS_METHOD_CALLS = true;
    myFixture.enableInspections(castInspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIgnoreSuspicious() {
    final RedundantCastInspection castInspection = new RedundantCastInspection();
    castInspection.IGNORE_SUSPICIOUS_METHOD_CALLS = true;
    myFixture.enableInspections(castInspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testDifferentNullness() { doTest(); }
  public void testSuspiciousVarargsCall() { doTest(); }

  public void testPrimitiveWidening() { doTest(); }
  public void testCastLongLiteral() { doTest(); }
}