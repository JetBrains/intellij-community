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

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class StreamPostfixTemplateTest extends PostfixTemplateTestCase {
  private LanguageLevel myDefaultLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(myFixture.getProject());
    myDefaultLanguageLevel = levelProjectExtension.getLanguageLevel();
    levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(myDefaultLanguageLevel);
      myDefaultLanguageLevel = null;
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "stream";
  }

  public void testSimple() {
    doTest();
  }

  public void testExpressionContext() {
    doTest();
  }

  public void testNotAvailable() {
    doTest();
  }

  public void testDoNotExpandOnJavaLess8() {
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_6, myFixture.getTestRootDisposable());
    doTest();
  }
}

