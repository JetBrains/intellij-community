// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.shift;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.ui.ChooserInterceptor;
import java.util.regex.Pattern;
import com.intellij.ui.UiInterceptors;
import com.siyeh.ipp.IPPTestCase;

public class ReplaceShiftWithMultiplyIntentionTest extends IPPTestCase {

  public void testLeftShift() { doTest("Replace '<<' with '*'"); }
  public void testLongShift() { doTest("Replace '<<' with '*'"); }
  public void testLargeShift() { doTest("Replace '<<' with '*'"); }
  public void testLeftShiftAssign() { doTest("Replace '<<=' with '*='"); }
  public void testLongShiftAssign() { doTest("Replace '<<=' with '*='"); }
  public void testParentheses() { doTest("Replace '<<' with '*'"); }

  public void testRightShift() { doTest("Replace '>>' with division"); }
  public void testRightShiftNonDefaultChooser() {
    doTestWithChooser("RightShift", ">>", "Replace '>>' with '/' (may change semantics)");
  }
  public void testRightShiftJava7() { doTestJava7("RightShift", "Replace '>>' with '/' (may change semantics)"); }

  public void testRightShiftAssign() {
    doTest("Replace '>>=' with division");
  }
  public void testRightShiftAssignNonDefaultChooser() {
    doTestWithChooser("RightShiftAssign", ">>=", "Replace '>>=' with '/=' (may change semantics)");
  }
  public void testRightShiftAssignJava7() {
    doTestJava7("RightShiftAssign", "Replace '>>=' with '/=' (may change semantics)");
  }

  public void testRightShiftPos() { doTest("Replace '>>' with '/'"); }
  public void testRightShiftAssignPos() { doTest("Replace '>>=' with '/='"); }

  @Override
  protected String getRelativePath() {
    return "shift/replace_shift_with_multiply";
  }

  private void doTestJava7(String testName, String intentionName) {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_7, () -> {
      CodeInsightTestUtil.doIntentionTest(myFixture, intentionName, testName + ".java", testName + "_semChange_after.java");
    });
  }

  private void doTestWithChooser(String testName, String operator, String selectedOption) {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(selectedOption)));
    CodeInsightTestUtil.doIntentionTest(
      myFixture,
      "Replace '" + operator + "' with division",
      testName + ".java",
      testName + "_semChange_after.java"
    );
  }
}
