// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import static com.intellij.testFramework.utils.EncodingManagerUtilKt.doEncodingTest;

public class ConvertToBasicLatinTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/convertToBasicLatin/";
  }

  public void testCharLiteral() { doTest(); }

  public void testStringLiteral() { doTest(); }

  public void testPlainComment() { doTest(); }

  public void testDocComment() { doTest(); }

  public void testDocTag() { doTest(); }

  private void doTest() {
    doEncodingTest(getProject(), "UTF-8", null, () -> {
      final String name = getTestName(false);
      String intention = CodeInsightBundle.message("intention.convert.to.basic.latin");
      CodeInsightTestUtil.doIntentionTest(myFixture, intention, name + ".java", name + "_after.java");
    });
  }
}
