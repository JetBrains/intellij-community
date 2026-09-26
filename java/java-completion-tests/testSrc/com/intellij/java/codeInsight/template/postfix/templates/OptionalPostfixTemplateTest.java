// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class OptionalPostfixTemplateTest extends PostfixTemplateTestCase {
  private LanguageLevel myDefaultLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_8);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), myDefaultLanguageLevel);
      myDefaultLanguageLevel = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "opt";
  }

  public void testExpression() {
    doTest();
  }

  public void testBoxedType() {
    doTest();
  }

  public void testPrimitiveType() {
    doTest();
  }
  
  public void testIntLiteral() {
    doTest();
  }

  public void testArray() {
    doTest();
  }

  public void testInt() {
    doTest();
  }

  public void testDouble() {
    doTest();
  }
  
  public void testLong() {
    doTest();
  }

  public void testInReturn() {
    doTest();
  }

  @NeedsIndex.SmartMode(reason = "Requires nullability analysis")
  public void testNotNullMethodCall() {
    myFixture.addClass("package org.jetbrains.annotations;" +
                       "public @interface NotNull {}");
    doTest();
  }
  
  public void testDoNotExpandOnJavaLess8() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_6, myFixture.getTestRootDisposable());
    doTest();
  }

  public static class ModOptionalPostfixTemplateTest extends OptionalPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

