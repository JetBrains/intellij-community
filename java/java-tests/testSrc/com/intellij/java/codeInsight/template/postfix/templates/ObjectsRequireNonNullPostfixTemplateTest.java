// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class ObjectsRequireNonNullPostfixTemplateTest extends PostfixTemplateTestCase {
  private LanguageLevel myDefaultLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_7);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), myDefaultLanguageLevel);
      myDefaultLanguageLevel = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSimple() {
    doTest();
  }

  public void testPrimitive() {
    doTest();
  }

  public void testAssignment() {
    doTest();
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "reqnonnull";
  }
}
