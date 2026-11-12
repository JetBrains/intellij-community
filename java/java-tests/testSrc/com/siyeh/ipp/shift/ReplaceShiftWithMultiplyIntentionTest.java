// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.shift;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ReplaceShiftWithMultiplyIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "shift/replace_shift_with_multiply");

  @Test
  public void testLeftShift() { ipp.get().doTest("Replace '<<' with '*'"); }
  @Test
  public void testLongShift() { ipp.get().doTest("Replace '<<' with '*'"); }
  @Test
  public void testLargeShift() { ipp.get().doTest("Replace '<<' with '*'"); }
  @Test
  public void testLeftShiftAssign() { ipp.get().doTest("Replace '<<=' with '*='"); }
  @Test
  public void testLongShiftAssign() { ipp.get().doTest("Replace '<<=' with '*='"); }
  @Test
  public void testParentheses() { ipp.get().doTest("Replace '<<' with '*'"); }

  @Test
  public void testRightShift() { ipp.get().doTest("Replace '>>' with division"); }
  @Test
  public void testRightShiftNonDefaultChooser() {
    doTestWithChooser("RightShift", ">>", "Replace '>>' with '/' (may change semantics)");
  }
  @Test
  public void testRightShiftJava7() { doTestJava7("RightShift", "Replace '>>' with '/' (may change semantics)"); }

  @Test
  public void testRightShiftAssign() {
    ipp.get().doTest("Replace '>>=' with division");
  }
  @Test
  public void testRightShiftAssignNonDefaultChooser() {
    doTestWithChooser("RightShiftAssign", ">>=", "Replace '>>=' with '/=' (may change semantics)");
  }
  @Test
  public void testRightShiftAssignJava7() {
    doTestJava7("RightShiftAssign", "Replace '>>=' with '/=' (may change semantics)");
  }

  @Test
  public void testRightShiftPos() { ipp.get().doTest("Replace '>>' with '/'"); }
  @Test
  public void testRightShiftAssignPos() { ipp.get().doTest("Replace '>>=' with '/='"); }

  private void doTestJava7(String testName, String intentionName) {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_7, () -> {
      EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestUtil.doIntentionTest(myFixture, intentionName, testName + ".java", testName + "_semChange_after.java"));
    });
  }

  private void doTestWithChooser(String testName, String operator, String selectedOption) {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(selectedOption)));
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeInsightTestUtil.doIntentionTest(
        myFixture,
        "Replace '" + operator + "' with division",
        testName + ".java",
        testName + "_semChange_after.java"
      );
    });
  }
}
