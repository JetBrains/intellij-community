// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;


public class ConvertToStringLiteralTest extends JavaCodeInsightFixtureTestCase {
  private String myIntention;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIntention = QuickFixBundle.message("convert.to.string.text");
  }

  public void testSimple() {
    CodeInsightTestUtil.doIntentionTest(myFixture, myIntention, "Simple.java", "Simple_after.java");
  }

  public void testAvailableOnce() {
    myFixture.configureByFile(getTestName(false) + ".java");
    assertSize(1, ContainerUtil.filter(myFixture.getAvailableIntentions(), a -> myIntention.equals(a.getText())));
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/convertToStringLiteral/";
  }
}
