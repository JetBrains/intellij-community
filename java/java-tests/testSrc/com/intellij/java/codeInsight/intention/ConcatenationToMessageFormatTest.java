// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.ArrayList;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ConcatenationToMessageFormatTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/concatenationToMessageFormat/");

  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testConstant() {
    assertTestNotAvailable();
  }

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testComments() { doTest(); }
  @Test
  public void testTextblock() { doTest(); }
  @Test
  public void testEscaping() { doTest(); }

  private void doTest() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = testName;
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeInsightTestUtil
        .doIntentionTest(myFixture, "Replace '+' with 'java.text.MessageFormat.format()'", name + ".java", name + ".after.java");
    });
  }

  private void assertTestNotAvailable() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".java");
    assertThat(myFixture.filterAvailableIntentions("Replace '+' with 'java.text.MessageFormat.format()'")).isNullOrEmpty();
  }

  private void doTest(String expressionText, String messageFormatText, String... foundExpressionTexts) {
    ReadAction.runBlocking(() -> {
      final PsiExpression expression = JavaPsiFacade.getElementFactory(codeInsightFixture.get().getProject()).createExpressionFromText(expressionText, null);
      final ArrayList<PsiExpression> args = new ArrayList<>();
      final String formatString = PsiConcatenationUtil.buildUnescapedFormatString(expression, false, args);
      assertEquals(messageFormatText, formatString);
      assertEquals(foundExpressionTexts.length, args.size());
      for (int i = 0; i < foundExpressionTexts.length; i++) {
        assertEquals(foundExpressionTexts[i], args.get(i).getText());
      }
    });
  }

  @Test
  public void test1() {
    doTest("\"aaa 'bbb' '\" + ((java.lang.String)ccc) + \"'\"", "aaa ''bbb'' ''{0}''", "ccc");
  }

  @Test
  public void test2() {
    doTest("1 + 2 + 3 + \"{}'\" + '\\n' + ((java.lang.String)ccc)", "{0}'{}'''\n{1}", "1 + 2 + 3", "ccc");
  }

  @Test
  public void test3() {
    doTest("\"Test{A = \" + 1 + \", B = \" + 2 + \", C = \" + 3 + \"}\"", "Test'{'A = {0}, B = {1}, C = {2}'}'", "1", "2", "3");
  }

  @Test
  public void testNullCast() {
    doTest("\"abc\" + (String)()", "abc{0}", "(String)()");
  }
}
