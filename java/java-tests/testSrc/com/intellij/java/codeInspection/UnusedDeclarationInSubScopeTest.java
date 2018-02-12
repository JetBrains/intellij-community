/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

public class UnusedDeclarationInSubScopeTest extends AbstractUnusedDeclarationTest {
  @Override
  protected void setupRootModel(@NotNull String testDir, @NotNull VirtualFile[] sourceDir, String jdkName) {
    super.setupRootModel(testDir, sourceDir, jdkName);
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(testDir);
    assertNotNull(projectDir);
    VirtualFile test = projectDir.findChild("test_src");
    if (test != null) PsiTestUtil.addSourceRoot(myModule, test, true);
  }

  @NotNull
  @Override
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    VirtualFile[] roots = ModuleRootManager.getInstance(myModule).getSourceRoots(false);
    assertEquals(1, roots.length);
    PsiManager psiManager = PsiManager.getInstance(myProject);
    return new AnalysisScope(psiManager.findDirectory(roots[0]));
  }

  public void testParameterUsedInOutOfScopeOverrider() {
    doTest();
  }

  @Override
  protected void doTest() {
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }
}