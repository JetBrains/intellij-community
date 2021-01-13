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
    final String previewFeatureAnnotation = BASE_PATH + "/PreviewFeature.java";
    final String previewFeatureAnnotationNew = BASE_PATH + "/JdkInternalJavacPreviewFeature.java";
    final String packagePreview = BASE_PATH + "/packagepreview/package-info.java";
    final String interfaceInPreviewPackage = BASE_PATH + "/packagepreview/FromPreview.java";
    final String moduleInfo = BASE_PATH + "/packagepreview/module-info.java";

    final String packagePreviewNew = BASE_PATH + "/jdk.internal.javac.packagepreview/package-info.java";
    final String interfaceInPreviewPackageNew = BASE_PATH + "/jdk.internal.javac.packagepreview/FromPreview.java";
    final String moduleInfoNew = BASE_PATH + "/jdk.internal.javac.packagepreview/module-info.java";

    myFixture.configureByFile(previewFeatureAnnotation);
    myFixture.configureByFile(previewFeatureAnnotationNew);
    myFixture.configureByFile(packagePreview);
    myFixture.configureByFile(interfaceInPreviewPackage);
    myFixture.configureByFile(moduleInfo);

    myFixture.configureByFile(packagePreviewNew);
    myFixture.configureByFile(interfaceInPreviewPackageNew);
    myFixture.configureByFile(moduleInfoNew);

    final String packagePreviewImpl = BASE_PATH + "/packagepreview/impl/package-info.java";
    final String fromPreviewImpl = BASE_PATH + "/packagepreview/impl/FromPreviewImpl.java";

    final String packagePreviewImplNew = BASE_PATH + "/jdk.internal.javac.packagepreview/impl/package-info.java";
    final String fromPreviewImplNew = BASE_PATH + "/jdk.internal.javac.packagepreview/impl/FromPreviewImpl.java";

    myFixture.configureByFile(packagePreviewImpl);
    myFixture.configureByFile(fromPreviewImpl);

    myFixture.configureByFile(packagePreviewImplNew);
    myFixture.configureByFile(fromPreviewImplNew);
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
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
