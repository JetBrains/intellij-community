// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author Dmitry Batkovich
 */
public class CreateSwitchTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/createSwitch/";
  }

  public void testEnum() { doTest(); }
  public void testEnum2() { doTest(); }
  public void testString() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_7, this::doTest); }
  public void testPrimitive() { doTest(); }
  public void testBoxedType() { doTest(); }
  public void testNotAvailable() { doTestNotAvailable(); }
  public void testNotAvailable2() { doTestNotAvailable(); }
  public void testNotAvailableInForUpdate() { doTestNotAvailable(); }
  public void testNotAvailableInAssignment() { doTestNotAvailable(); }
  public void testNotAvailableOnRedCode() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_7, this::doTestNotAvailable); }
  public void testNotFailingOnBadEscapes() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_7, this::doTestNotAvailable); }
  public void testNotAvailableOnLiteral() { doTestNotAvailable(); }
  public void testPatternMatching() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest); }
  public void testNoPatternMatching() { doTestNotAvailable(); }

  private void doTest() {
    final String name = getTestName(true);
    CodeInsightTestUtil.doIntentionTest(myFixture, JavaBundle.message("intention.create.switch.statement"), name + ".java", name + "_after.java");
  }

  private void doTestNotAvailable() {
    myFixture.configureByFile(getTestName(true) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions(JavaBundle.message("intention.create.switch.statement")));
  }
}
