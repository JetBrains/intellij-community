// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.memory;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class InnerClassMayBeStaticInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new InnerClassMayBeStaticInspection());

  @BeforeEach
  void setUp() {
    inspectionFixture.get().addEnvironmentClass("package org.junit.jupiter.api;" +
                        "public @interface Nested {}");
  }

  @Test
  public void testInnerClassMayBeStatic() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15, () -> {
      inspectionFixture.get().doTest();
      inspectionFixture.get().checkQuickFixAll();
    });
  }

  @Test
  public void testInnerStaticsJDK16() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_16, () -> {
      inspectionFixture.get().doTest();
      inspectionFixture.get().checkQuickFixAll();
    });
  }

  @Test
  public void testImplicitClassNoFix() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      inspectionFixture.get().doTest();
      inspectionFixture.get().checkQuickFixAll();
    });
  }

  @Test
  public void testNestedClassesImplicitClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      inspectionFixture.get().doTest();
      inspectionFixture.get().checkQuickFixAll();
    });
  }
}