// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class CopyTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/copy/multifile/";
  }

  public void testCopyAvailable() throws Exception {
    doTest();
  }

  public void testJavaAndTxt() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    PsiPackage pack1 = myFixture.findPackage("pack1");
    PsiPackage pack2 = myFixture.findPackage("pack2");
    assertTrue(CopyHandler.canCopy(new PsiElement[]{pack1.getDirectories()[0], pack2.getDirectories()[0]}));
  }

  public void testMultipleClasses() throws Exception {
    VirtualFile root = myFixture.copyDirectoryToProject(getTestName(true), "");
    final PsiClass aClass = myFixture.findClass("pack1.Klass");
    
    final PsiFile containingFile = aClass.getContainingFile();

    assertTrue(CopyHandler.canCopy(new PsiElement[]{containingFile}));
    assertFalse(CopyHandler.canClone(new PsiElement[]{containingFile}));

    PsiPackage pack2 = myFixture.findPackage("pack2");
    final PsiDirectory targetDirectory = pack2.getDirectories()[0];
    CopyHandler.doCopy(new PsiElement[]{containingFile}, targetDirectory);

    VirtualFile fileAfter = root.findFileByRelativePath("pack2/Klass.java");
    VirtualFile fileExpected = root.findFileByRelativePath("pack2/Klass.expected.java");

    PlatformTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  public void testMultipleFiles() throws Exception {
    VirtualFile root = myFixture.copyDirectoryToProject(getTestName(true), "");

    final VirtualFile first = root.findFileByRelativePath("from/1.txt");
    assertNotNull(first);
    final VirtualFile second = root.findFileByRelativePath("from/2.txt");
    assertNotNull(second);

    final PsiFile firstPsi = getPsiManager().findFile(first);
    final PsiFile secondPsi = getPsiManager().findFile(second);

    assertTrue(CopyHandler.canCopy(new PsiElement[]{firstPsi, secondPsi}));

    final VirtualFile toDir = root.findChild("to");
    assertNotNull(toDir);
    final PsiDirectory targetDirectory = getPsiManager().findDirectory(toDir);

    CopyHandler.doCopy(new PsiElement[]{firstPsi, secondPsi}, targetDirectory);

    assertNotNull(root.findFileByRelativePath("to/1.txt"));
    assertNotNull(root.findFileByRelativePath("to/2.txt"));
  }

  public void testPackageInfo() throws Exception {
    VirtualFile root = myFixture.copyDirectoryToProject(getTestName(true), "");
    final VirtualFile first = root.findFileByRelativePath("from/package-info.java");
    assertNotNull(first);
    
    final PsiFile firstPsi = getPsiManager().findFile(first);

    assertTrue(CopyHandler.canCopy(new PsiElement[]{firstPsi}));

    final VirtualFile toDir = root.findChild("to");
    assertNotNull(toDir);
    final PsiDirectory targetDirectory = getPsiManager().findDirectory(toDir);

    CopyHandler.doCopy(new PsiElement[]{firstPsi}, targetDirectory);

    final VirtualFile dest = root.findFileByRelativePath("to/package-info.java");
    assertNotNull(dest);

    VirtualFile fileExpected = root.findFileByRelativePath("to/package-info.expected.java");
    
    PlatformTestUtil.assertFilesEqual(fileExpected, dest);
  }
}
