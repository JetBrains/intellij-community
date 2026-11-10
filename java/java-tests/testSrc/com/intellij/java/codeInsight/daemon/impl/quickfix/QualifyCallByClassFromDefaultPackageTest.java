// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class QualifyCallByClassFromDefaultPackageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String QUALIFY_METHOD_FIX_TEXT = "Qualify static call 'Util.staticMethod()'";
  private static final String QUALIFY_CONST_FIX_TEXT = "Qualify static constant access 'Util.STATIC_FIELD'";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public class Util {" +
                       " public static void staticMethod() {}" +
                       " public static final String STATIC_FIELD = \"XXX\";" +
                       "}");
  }

  @Test
  public void testMethodCallFromDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", "class Main {" +
                                           " void m() { staticMeth<caret>od(); }" +
                                           "}");
    assertFixAvailable(QUALIFY_METHOD_FIX_TEXT, "class Main {" +
                                               " void m() { Util.staticMethod(); }" +
                                               "}");
  }

  @Test
  public void testMethodCallFromNonDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", "package org.some; " +
                                           "class Main {" +
                                           " void m() { staticMeth<caret>od(); }" +
                                           "}");
    assertThat(myFixture.filterAvailableIntentions(QUALIFY_METHOD_FIX_TEXT)).isNullOrEmpty();
  }

  @Test
  public void testFieldCallFromDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", "class Main {" +
                                           " void m() { STATIC_FIE<caret>LD; }" +
                                           "}");
    assertFixAvailable(QUALIFY_CONST_FIX_TEXT, "class Main {" +
                                               " void m() { Util.STATIC_FIELD; }" +
                                               "}");
  }

  protected void assertFixAvailable(String hint, String afterText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.launchAction(assertThat(myFixture.filterAvailableIntentions(hint)).singleElement().actual());
    myFixture.checkResult(afterText);
  }

  @Test
  public void testFieldCallFromNonDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", "package org.some; " +
                                           "class Main {" +
                                           " void m() { STATIC_FIE<caret>LD; }" +
                                           "}");
    assertThat(myFixture.filterAvailableIntentions(QUALIFY_CONST_FIX_TEXT)).isNullOrEmpty();
  }

}

