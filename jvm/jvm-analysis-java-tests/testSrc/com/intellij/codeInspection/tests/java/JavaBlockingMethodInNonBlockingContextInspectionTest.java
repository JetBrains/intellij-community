// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java;

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;

import static com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection.DEFAULT_BLOCKING_ANNOTATIONS;
import static com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection.DEFAULT_NONBLOCKING_ANNOTATIONS;

public class JavaBlockingMethodInNonBlockingContextInspectionTest extends UsefulTestCase {

  private CodeInsightTestFixture myFixture;

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture>
      projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());

    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final String dataPath = PathManagerEx.getTestDataPath() + "/codeInspection/blockingCallsDetection";
    myFixture.setTestDataPath(dataPath);
    final JavaModuleFixtureBuilder builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);

    myFixture.setUp();
    Module module = builder.getFixture().getModule();
    ModuleRootModificationUtil.updateModel(module, model -> {
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath());
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(myFixture.getTempDirPath())});
    });

    Project project = myFixture.getProject();

    JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = true;

    BlockingMethodInNonBlockingContextInspection myInspection = new BlockingMethodInNonBlockingContextInspection();
    myInspection.myBlockingAnnotations = DEFAULT_BLOCKING_ANNOTATIONS;
    myInspection.myNonBlockingAnnotations = DEFAULT_NONBLOCKING_ANNOTATIONS;
    myFixture.enableInspections(myInspection);
  }

  public void testSimpleAnnotationDetection() {
    myFixture.configureByFiles("TestSimpleAnnotationsDetection.java", "Blocking.java", "NonBlocking.java");
    myFixture.testHighlighting(true, false, true, "TestSimpleAnnotationsDetection.java");
  }

  public void testClassAnnotationDetection() {
    myFixture.configureByFiles("TestClassAnnotationsDetection.java", "Blocking.java", "NonBlocking.java");
    myFixture.testHighlighting(true, false, true, "TestClassAnnotationsDetection.java");
  }

  public void testExternalBlockingAnnotationDetection() {
    myFixture.configureByFiles("TestExternalAnnotationsDetection.java", "Blocking.java", "NonBlocking.java", "annotations.xml");
    myFixture.testHighlighting(true, false, true, "TestExternalAnnotationsDetection.java");
  }

  public void testThrowsTypeDetection() {
    myFixture.configureByFiles("TestThrowsTypeDetection.java", "Blocking.java", "NonBlocking.java");
    myFixture.testHighlighting(true, false, true, "TestThrowsTypeDetection.java");
  }
}
