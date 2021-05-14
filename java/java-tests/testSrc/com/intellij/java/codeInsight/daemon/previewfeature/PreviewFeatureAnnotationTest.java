// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.previewfeature;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PreviewFeatureAnnotationTest extends LightJavaCodeInsightFixtureTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/previewfeature";

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    configureByFiles(BASE_PATH + "/PreviewFeature.java"
      , BASE_PATH + "/JdkInternalJavacPreviewFeature.java"
      , BASE_PATH + "/packagepreview/package-info.java"
      , BASE_PATH + "/packagepreview/FromPreview.java"
      , BASE_PATH + "/packagepreview/module-info.java"
      , BASE_PATH + "/jdk.internal.javac.packagepreview/package-info.java"
      , BASE_PATH + "/jdk.internal.javac.packagepreview/FromPreview.java"
      , BASE_PATH + "/jdk.internal.javac.packagepreview/module-info.java"
      , BASE_PATH + "/packagepreview/impl/package-info.java"
      , BASE_PATH + "/packagepreview/impl/FromPreviewImpl.java"
      , BASE_PATH + "/jdk.internal.javac.packagepreview/impl/package-info.java"
      , BASE_PATH + "/jdk.internal.javac.packagepreview/impl/FromPreviewImpl.java");
  }

  private void configureByFiles(String... files) {
    for (String file : files) {
      myFixture.configureByFile(file);
    }
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  public void testCallMethodsWithPreviewFeature() { doTest(); }
  public void testCallConstructorWithPreviewFeature() { doTest(); }
  public void testTypeWithPreviewFeature() { doTest(); }
  public void testFieldsWithPreviewFeature() { doTest(); }
  public void testImportWithPreviewFeature() { doTest(); }
  public void testReferenceWithPreviewFeature() { doTest(); }
  public void testRequiresModuleWithPreviewFeature() { doTest(); }
  public void testMethodReferenceWithPreviewFeature() { doTest(); }
  public void testClassImplementsWithPreviewFeature() { doTest(); }

  public void testCallMethodsWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testCallConstructorWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testReferenceWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testTypeWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testFieldsWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testImportWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testRequiresModuleWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testMethodReferenceWithJdkInternalJavacPreviewFeature() { doTest(); }
  public void testClassImplementsWithJdkInternalJavacPreviewFeature() { doTest(); }

  private void doTest() {
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFiles(filePath);
    myFixture.checkHighlighting();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
