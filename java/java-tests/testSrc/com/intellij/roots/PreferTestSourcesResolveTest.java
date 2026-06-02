// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

public class PreferTestSourcesResolveTest extends JavaModuleTestCase {
  private static final String KEY = "java.resolve.prefer.test.sources";

  private LightTempDirTestFixtureImpl myFixture;
  private VirtualFile myProductionTest; // src/org/test/model/Test.java   -> getNum()
  private VirtualFile myTestTest;       // test/org/test/model/Test.java  -> getNum2()

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture = new LightTempDirTestFixtureImpl();
    myFixture.setUp();

    Module module = createModule("mod.iml", JavaModuleType.getModuleType());
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile srcRoot = myFixture.findOrCreateDir("src");
      VirtualFile testRoot = myFixture.findOrCreateDir("test");
      PsiTestUtil.addSourceRoot(module, srcRoot, false);
      PsiTestUtil.addSourceRoot(module, testRoot, true);
    });

    myProductionTest = myFixture.createFile("src/org/test/model/Test.java",
                                            "package org.test.model;\npublic class Test { public int getNum() { return 1; } }");
    myTestTest = myFixture.createFile("test/org/test/model/Test.java",
                                      "package org.test.model;\npublic class Test { public int getNum2() { return 2; } }");
  }

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

  private void enablePreferTestSources(boolean enabled) {
    Registry.get(KEY).setValue(enabled, getTestRootDisposable());
  }

  private PsiClass resolveParameterType(@NotNull VirtualFile viewerFile) {
    PsiJavaFile psiFile = (PsiJavaFile)PsiManager.getInstance(getProject()).findFile(viewerFile);
    assertNotNull(psiFile);
    PsiClass viewer = psiFile.getClasses()[0];
    PsiParameter parameter = viewer.getMethods()[0].getParameterList().getParameters()[0];
    return PsiUtil.resolveClassInClassTypeOnly(parameter.getType());
  }

  private PsiMethod resolveSingleCall(@NotNull VirtualFile viewerFile) {
    PsiJavaFile psiFile = (PsiJavaFile)PsiManager.getInstance(getProject()).findFile(viewerFile);
    assertNotNull(psiFile);
    PsiClass viewer = psiFile.getClasses()[0];
    PsiMethodCallExpression call = PsiTreeUtil.findChildOfType(viewer.getMethods()[0].getBody(), PsiMethodCallExpression.class);
    assertNotNull(call);
    return call.resolveMethod();
  }

  public void testSamePackageResolvesToTestSource() throws Exception {
    enablePreferTestSources(true);
    VirtualFile viewer = myFixture.createFile(
      "test/org/test/model/Viewer.java",
      "package org.test.model;\nclass Viewer { void m(Test t) { t.getNum2(); } }");

    PsiClass resolved = resolveParameterType(viewer);
    assertNotNull(resolved);
    assertEquals(myTestTest, resolved.getContainingFile().getVirtualFile());

    PsiMethod method = resolveSingleCall(viewer);
    assertNotNull("getNum2() must resolve when the test-source class shadows production", method);
  }

  public void testDifferentPackageResolvesToTestSource() throws Exception {
    enablePreferTestSources(true);
    VirtualFile viewer = myFixture.createFile(
      "test/org/test/other/Viewer.java",
      "package org.test.other;\nimport org.test.model.Test;\nclass Viewer { void m(Test t) { t.getNum2(); } }");

    PsiClass resolved = resolveParameterType(viewer);
    assertNotNull(resolved);
    assertEquals(myTestTest, resolved.getContainingFile().getVirtualFile());

    PsiMethod method = resolveSingleCall(viewer);
    assertNotNull("getNum2() must resolve when the test-source class shadows production", method);
  }

  public void testProductionCallerStillResolvesToProduction() throws Exception {
    enablePreferTestSources(true);
    VirtualFile viewer = myFixture.createFile(
      "src/org/test/model/ViewerMain.java",
      "package org.test.model;\nclass ViewerMain { void m(Test t) { t.getNum(); } }");

    PsiClass resolved = resolveParameterType(viewer);
    assertNotNull(resolved);
    assertEquals(myProductionTest, resolved.getContainingFile().getVirtualFile());

    PsiMethod method = resolveSingleCall(viewer);
    assertNotNull(method);
  }
}
