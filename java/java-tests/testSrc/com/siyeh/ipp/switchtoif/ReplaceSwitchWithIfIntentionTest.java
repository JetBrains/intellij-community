// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.switchtoif;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ReplaceSwitchWithIfIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_19);
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "switchtoif/replaceSwitchToIf", CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.SWITCH, JavaKeywords.IF));

  @Test
  public void testReplaceInt() {
    ipp.get().doTest();
  }

  @Test
  public void testReplaceInteger() {
    ipp.get().doTest();
  }

  @Test
  public void testReplaceChar() {
    ipp.get().doTest();
  }

  @Test
  public void testReplaceCharacter() {
    ipp.get().doTest();
  }

  @Test
  public void testDefaultOnly() {
    ipp.get().assertIntentionNotAvailable();
  }

  @Test
  public void testReplaceRecordPattern() {
    ipp.get().doTest();
  }

  @Test
  public void testReplaceEnum() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_6, () -> {
      ipp.get().doTest();
    });
  }

  @Test
  public void testReplaceWithBoolean1() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      ipp.get().doTest();
    });
  }

  @Test
  public void testReplaceWithBoolean2() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      ipp.get().doTest();
    });
  }

  @Test
  public void testReplaceWithBoolean3() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      ipp.get().doTest();
    });
  }

  @Test
  public void testReplaceWithBoolean4() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      ipp.get().doTest();
    });
  }
}
