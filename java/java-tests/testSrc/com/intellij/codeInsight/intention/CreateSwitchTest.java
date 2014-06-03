/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.impl.CreateSwitchIntention;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
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

  public void testEnum() {
    doTest();
  }

  public void testEnum2() {
    doTest();
  }

  public void testString() {
    doTestString();
  }

  public void testPrimitive() {
    doTest();
  }

  public void testNotAvailable() {
    doTestNotAvailable();
  }

  public void testNotAvailable2() {
    doTestNotAvailable();
  }

  private void doTestString() {
    final LanguageLevelProjectExtension languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel oldLanguageLevel = languageLevelProjectExtension.getLanguageLevel();
    languageLevelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_7);
    try {
      doTest();
    }
    finally {
      languageLevelProjectExtension.setLanguageLevel(oldLanguageLevel);
    }
  }

  private void doTest() {
    final String name = getTestName(true);
    CodeInsightTestUtil.doIntentionTest(myFixture, CreateSwitchIntention.TEXT, name + ".java", name + "_after.java");
  }

  private void doTestNotAvailable() {
    myFixture.configureByFile(getTestName(true) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions(CreateSwitchIntention.TEXT));
  }
}
