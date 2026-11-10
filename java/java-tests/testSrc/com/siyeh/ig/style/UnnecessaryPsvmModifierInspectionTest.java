// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class UnnecessaryPsvmModifierInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/style/unnecessary_psvm_modifier");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnnecessaryModifierInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testOldJavaWithModifiers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  }

  @Test
  public void testImplicitClassWithModifiers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> doTest());
  }

  @Test
  public void testNormalClassWithModifiers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> doTest());
  }

  @Test
  public void testNormalClassWithModifiersClassIsUsed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.addClass("""
                           import test.NormalClassWithModifiersClassIsUsed;
                           class Foo {
                             public static void main(String[] args){
                               NormalClassWithModifiersClassIsUsed.main(args);
                             }
                           }
                           """);
      doTest();
    });
  }
}
