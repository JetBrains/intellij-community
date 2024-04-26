/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
}

