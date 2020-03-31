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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class NavigateToAlreadyDeclaredVariableFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNavigateToVariableOfDifferentType() {
    myFixture.configureByText("A.java", "class A {{int i = 0; i++; long <caret>i = 0;}}");
    IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("navigate.variable.declaration.text", "i"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(14, myFixture.getCaretOffset());
  }

  public void testNavigateFromParameter() {
    myFixture.configureByText("A.java", "class A {void f(String[] elements){\n" +
                                        "        String element = \"hello\";\n" +
                                        "for (String el<caret>ement : elements){}" +
                                        "}}");
    IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("navigate.variable.declaration.text", "element"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(51, myFixture.getCaretOffset());
  }
}
