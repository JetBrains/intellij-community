// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class StaticImportFromDefaultPackageTest extends LightJavaCodeInsightFixtureTestCase {
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
