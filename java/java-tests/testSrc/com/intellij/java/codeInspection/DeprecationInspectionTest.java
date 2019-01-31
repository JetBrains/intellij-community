// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class DeprecationInspectionTest extends InspectionTestCase {

  private final DefaultLightProjectDescriptor myProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      model.getModuleExtension(JavaModuleExternalPaths.class)
        .setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(getTestDataPath() + "/deprecation/" + getTestName(true) + "/extAnnotations"))});
    }

    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk9();
    }
  };

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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return myProjectDescriptor;
  }

  public void testExternallyDeprecatedDefaultConstructor() {
    doTest();
  }
}
