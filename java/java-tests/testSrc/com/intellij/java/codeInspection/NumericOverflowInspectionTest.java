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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.NumericOverflowInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class NumericOverflowInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/numericOverflow";
  }

  public void testSimple() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new NumericOverflowInspection());
  }

  private void doTest() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testUpdatesOnTyping() {
    String text = "class My {\n" +
                  "void d(long lower) {\n" +
                  "        long upper =  lower + 1000<caret> * 31536000;\n" +
                  "    }" +
                  "}";
    myFixture.configureByText("My.java", text);
    assertOneElement(myFixture.doHighlighting(HighlightSeverity.WARNING));
    myFixture.type('L');
    assertEmpty(myFixture.doHighlighting(HighlightSeverity.WARNING));
    myFixture.type('\b');
    assertOneElement(myFixture.doHighlighting(HighlightSeverity.WARNING));
  }
}