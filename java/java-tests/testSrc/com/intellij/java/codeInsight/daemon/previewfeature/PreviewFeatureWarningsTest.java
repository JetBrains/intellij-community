// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.previewfeature;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.preview.PreviewFeatureInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class PreviewFeatureWarningsTest extends LightJavaCodeInsightFixtureTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/previewfeature/warnings";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PreviewFeatureInspection.class);
    myFixture.configureByFiles(
      BASE_PATH + "/../java.base/jdk.internal/PreviewFeature.java",
      BASE_PATH + "/../java.base/jdk.internal/FirstPreviewFeature.java"
    );
  }

  public void testImplementsPreviewFeature() { doTest(); }
  public void testAccessInnerClassInsidePreviewFeatureClass() { doTest(); }
  public void testAccessStaticMethodInPreviewFeatureClass() { doTest(); }
  public void testSuppressPreviewFeatureWarning() { doTest(); }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new ProjectDescriptor(LanguageLevel.JDK_17_PREVIEW);
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