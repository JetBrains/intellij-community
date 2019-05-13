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

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class QualifyCallByClassFromDefaultPackageTest extends LightCodeInsightFixtureTestCase {
  private static final String QUALIFY_METHOD_FIX_TEXT = "Qualify static call 'Util.staticMethod'";
  private static final String QUALIFY_CONST_FIX_TEXT = "Qualify static constant access 'Util.STATIC_FIELD'";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("public class Util {" +
                       " public static void staticMethod() {}" +
                       " public static final String STATIC_FIELD = \"XXX\";" +
                       "}");
  }

  public void testMethodCallFromDefaultPackage() {
    myFixture.configureByText("Main.java", "class Main {" +
                                           " void m() { staticMeth<caret>od(); }" +
                                           "}");
    assertFixAvailable(QUALIFY_METHOD_FIX_TEXT, "class Main {" +
                                               " void m() { Util.staticMethod(); }" +
                                               "}");
  }

  public void testMethodCallFromNonDefaultPackage() {
    myFixture.configureByText("Main.java", "package org.some; " +
                                           "class Main {" +
                                           " void m() { staticMeth<caret>od(); }" +
                                           "}");
    assertEmpty(myFixture.filterAvailableIntentions(QUALIFY_METHOD_FIX_TEXT));
  }

  public void testFieldCallFromDefaultPackage() {
    myFixture.configureByText("Main.java", "class Main {" +
                                           " void m() { STATIC_FIE<caret>LD; }" +
                                           "}");
    assertFixAvailable(QUALIFY_CONST_FIX_TEXT, "class Main {" +
                                               " void m() { Util.STATIC_FIELD; }" +
                                               "}");
  }

  protected void assertFixAvailable(String hint, String afterText) {
    myFixture.launchAction(assertOneElement(myFixture.filterAvailableIntentions(hint)));
    myFixture.checkResult(afterText);
  }

  public void testFieldCallFromNonDefaultPackage() {
    myFixture.configureByText("Main.java", "package org.some; " +
                                           "class Main {" +
                                           " void m() { STATIC_FIE<caret>LD; }" +
                                           "}");
    assertEmpty(myFixture.filterAvailableIntentions(QUALIFY_CONST_FIX_TEXT));
  }


}

