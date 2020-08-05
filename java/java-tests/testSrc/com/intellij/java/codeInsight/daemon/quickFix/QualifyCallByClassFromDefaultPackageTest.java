// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class QualifyCallByClassFromDefaultPackageTest extends LightJavaCodeInsightFixtureTestCase {
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

