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
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ConcatenationToMessageFormatTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/concatenationToMessageFormat/";
  }

  public void testConstant() {
    assertTestNotAvailable();
  }

  public void testSimple() {
    doTest();
  }

  private void doTest() {
    final String name = getTestName(true);
    CodeInsightTestUtil
      .doIntentionTest(myFixture, "Replace '+' with 'java.text.MessageFormat.format()'", name + ".java", name + ".after.java");
  }

  private void assertTestNotAvailable() {
    myFixture.configureByFile(getTestName(true) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions("Replace '+' with 'java.text.MessageFormat.format()'"));
  }
}
