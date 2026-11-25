// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class MissingOverrideAnnotationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MissingOverrideAnnotationInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testNotAvailable() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testHierarchy() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testHierarchy2() {
    inspectionFixture.get().doTest();
  }
  
  @Test
  public void testSimpleJava5() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_5, inspectionFixture.get()::doTest);
  }
  
  @Test
  public void testNotAvailableMethodInLanguageLevel7() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_7, inspectionFixture.get()::doTest);
  }
  
  public void _testRecordAccessorJava14() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.HIGHEST, inspectionFixture.get()::doTest);
  }
  
  @Test
  public void testRecordAccessorJava15() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.HIGHEST, inspectionFixture.get()::doTest);
  }

  @TestApplication
  public static class MissingOverrideAnnotationInspectionFixTest {
    private static final String FIX_TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/inheritance/missing_override_annotation";
    private static final TestFixture<LightJavaProject> fixProjectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
    private final TestFixture<LightQuickFixFixture> fixQuickFixFixture =
      lightQuickFixFixture(fixProjectFixture, FIX_TEST_DATA_PATH, () -> List.of(new MissingOverrideAnnotationInspection()));

    static List<String> files() {
      return lightQuickFixTestFiles(FIX_TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      fixQuickFixFixture.get().doTest(fileName);
    }
  }
}
