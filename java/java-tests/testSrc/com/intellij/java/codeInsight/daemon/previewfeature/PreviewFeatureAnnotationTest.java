// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    final String previewFeatureAnnotation = BASE_PATH + "/PreviewFeature.java";
    final String packagePreview = BASE_PATH + "/packagepreview/package-info.java";
    final String interfaceInPreviewPackage = BASE_PATH + "/packagepreview/FromPreview.java";
    final String moduleInfo = BASE_PATH + "/packagepreview/module-info.java";
    myFixture.configureByFile(previewFeatureAnnotation);
    myFixture.configureByFile(packagePreview);
    myFixture.configureByFile(interfaceInPreviewPackage);
    myFixture.configureByFile(moduleInfo);

    final String packagePreviewImpl = BASE_PATH + "/packagepreview.impl/package-info.java";
    final String fromPreviewImpl = BASE_PATH + "/packagepreview.impl/FromPreviewImpl.java";

    myFixture.configureByFile(packagePreviewImpl);
    myFixture.configureByFile(fromPreviewImpl);
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
