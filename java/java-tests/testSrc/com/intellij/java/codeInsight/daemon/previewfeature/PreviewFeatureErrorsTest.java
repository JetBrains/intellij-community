// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.previewfeature;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.preview.PreviewFeatureInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public final class PreviewFeatureErrorsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new ProjectDescriptor(LanguageLevel.JDK_15));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/previewfeature/errors";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
    myFixture.enableInspections(PreviewFeatureInspection.class);
    myFixture.configureByFiles(
      BASE_PATH + "/../java.base/jdk.internal.javac/PreviewFeature.java",
      BASE_PATH + "/../java.base/jdk.internal.javac/FirstPreviewFeature.java",
      BASE_PATH + "/../java.base/jdk.internal.javac/FirstPreviewFeatureReflective.java"
    );
  }

  @Test
  public void testImplementsPreviewFeature() { doTest(); }
  @Test
  public void testAccessInnerClassInsidePreviewFeatureClass() { doTest(); }
  @Test
  public void testAccessStaticMethodInPreviewFeatureClass() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String filePath = BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting();
  }
}