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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class StaticImportFromDefaultPackageTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("public class Util {" +
                       " public static void staticMethod() {}" +
                       " public static final String STATIC_FIELD = \"XXX\";" +
                       "}");
  }

  public void testMethodCallFromDefaultPackage() {
    myFixture.configureByText("Main.java", "class Main { void m () {" +
                                           " staticMeth<caret>od();" +
                                           "}}");
    assertFixForMethodIsNotAvailable();
  }

  public void testMethodCallFromNonDefaultPackage() {
    myFixture.configureByText("Main.java", "package org;" +
                                           "class Main { void m () {" +
                                           " staticMeth<caret>od();" +
                                           "}}");
    assertFixForMethodIsNotAvailable();
  }

  public void testFieldCallFromDefaultPackage() {
    myFixture.configureByText("Main.java", "class Main { void m () {" +
                                           " STATIC_<caret>FIELD();" +
                                           "}}");
    assertFixForFieldIsNotAvailable();
  }

  public void testFieldCallFromNonDefaultPackage() {
    myFixture.configureByText("Main.java", "package org;" +
                                           "class Main { void m () {" +
                                           " STATIC_<caret>FIELD();" +
                                           "}}");
    assertFixForFieldIsNotAvailable();
  }

  private void assertFixForMethodIsNotAvailable() {
    assertEmpty(myFixture.filterAvailableIntentions(QuickFixBundle.message("static.import.method.text")));
  }

  private void assertFixForFieldIsNotAvailable() {
    assertEmpty(myFixture.filterAvailableIntentions(QuickFixBundle.message("static.import.constant.text")));
  }
}
