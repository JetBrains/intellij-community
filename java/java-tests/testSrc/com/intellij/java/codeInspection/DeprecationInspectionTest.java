// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class DeprecationInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("deprecation/" + getTestName(true), new DeprecationInspection());
  }

  public void testDeprecatedMethod() {
    doTest();
  }

  public void testDeprecatedInImport() {
    doTest();
  }

  public void testDeprecatedInStaticImport() {
    doTest();
  }

  public void testDeprecatedInner() {
    doTest();
  }

  public void testDeprecatedField() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuper() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuperNotCalled() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorTypeParameter() {
    doTest();
  }

  public void testDeprecationOnVariableWithAnonymousClass() {
    doTest();
  }

  public void testDeprecatedAnnotationProperty() {
    doTest();
  }

  public void testMethodsOfDeprecatedClass() {
    final DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_METHODS_OF_DEPRECATED = false;
    doTest("deprecation/" + getTestName(true), tool);
  }

  /**
   * Sets up external deprecation annotations
   * for the current test module.
   */
  private void configureExternalAnnotationsUrls(String... urls) {
    ModuleRootModificationUtil.updateModel(myModule, (root) -> {
      JavaModuleExternalPaths extension = root.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(urls);
    });
  }

  public void testExternallyDeprecatedDefaultConstructor() {
    String url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(getTestDataPath()) + "/deprecation/externallyDeprecatedDefaultConstructor/extAnnotations");
    configureExternalAnnotationsUrls(url);
    doTest();
  }
}
