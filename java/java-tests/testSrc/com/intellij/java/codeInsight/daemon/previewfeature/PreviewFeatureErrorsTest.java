// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.previewfeature;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.preview.PreviewFeatureInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class PreviewFeatureErrorsTest extends LightJavaCodeInsightFixtureTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/previewfeature/errors";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PreviewFeatureInspection.class);
    myFixture.configureByFiles(
      BASE_PATH + "/../java.base/jdk.internal.javac/PreviewFeature.java",
      BASE_PATH + "/../java.base/jdk.internal.javac/FirstPreviewFeature.java",
      BASE_PATH + "/../java.base/jdk.internal.javac/FirstPreviewFeatureReflective.java"
    );
  }

  public void testImplementsPreviewFeature() { doTest(); }
  public void testAccessInnerClassInsidePreviewFeatureClass() { doTest(); }
  public void testAccessStaticMethodInPreviewFeatureClass() { doTest(); }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new ProjectDescriptor(LanguageLevel.JDK_15);
  }

  private void doTest() {
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}