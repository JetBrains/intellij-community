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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class StaticImportMethodWithCommonNameTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    int idx = 0;
    while (idx++ < 450) {
      myFixture.addClass("package a; class ATest" + idx + "{ public void format(String s, String s1) {}}");
    }
  }

  public void testFindStaticMember() {
    myFixture.configureByText("a.java", "class A { {String s = for<caret>mat(\"\",\"\");}}");
    final IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("static.import.method.text"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("import static java.lang.String.format;\n" +
                          "\n" +
                          "class A { {String s = format(\"\",\"\");}}", false);
  }
}
