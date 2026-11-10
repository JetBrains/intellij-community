// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
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
public class StaticImportFromDefaultPackageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

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
    myFixture.configureByText("Main.java", "class Main { void m () {" +
                                           " staticMeth<caret>od();" +
                                           "}}");
    assertFixForMethodIsNotAvailable();
  }

  @Test
  public void testMethodCallFromNonDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", "package org;" +
                                           "class Main { void m () {" +
                                           " staticMeth<caret>od();" +
                                           "}}");
    assertFixForMethodIsNotAvailable();
  }

  @Test
  public void testFieldCallFromDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", "class Main { void m () {" +
                                           " STATIC_<caret>FIELD();" +
                                           "}}");
    assertFixForFieldIsNotAvailable();
  }

  @Test
  public void testFieldCallFromNonDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", "package org;" +
                                           "class Main { void m () {" +
                                           " STATIC_<caret>FIELD();" +
                                           "}}");
    assertFixForFieldIsNotAvailable();
  }

  private void assertFixForMethodIsNotAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertThat(myFixture.filterAvailableIntentions(QuickFixBundle.message("static.import.method.text"))).isNullOrEmpty();
  }

  private void assertFixForFieldIsNotAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertThat(myFixture.filterAvailableIntentions(QuickFixBundle.message("static.import.constant.text"))).isNullOrEmpty();
  }
}
