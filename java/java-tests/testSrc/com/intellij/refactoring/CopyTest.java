/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

public class CopyTest extends CodeInsightTestCase {
  
  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/copy/multifile/" + getTestName(true);
  }

  public void testCopyAvailable() throws Exception {
    doTest();
  }

  public void testJavaAndTxt() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String rootBefore = getRoot();
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiPackage pack1 = myJavaFacade.findPackage("pack1");
    PsiPackage pack2 = myJavaFacade.findPackage("pack2");
    assertTrue(CopyHandler.canCopy(new PsiElement[]{pack1.getDirectories()[0], pack2.getDirectories()[0]}));
  }

  public void testMultipleClasses() throws Exception {
    String rootBefore = getRoot();
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    final VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    final PsiClass aClass = myJavaFacade.findClass("pack1.Klass");
    assertNotNull(aClass);

    final PsiFile containingFile = aClass.getContainingFile();

    assertTrue(CopyHandler.canCopy(new PsiElement[]{containingFile}));
    assertFalse(CopyHandler.canClone(new PsiElement[]{containingFile}));

    PsiPackage pack2 = myJavaFacade.findPackage("pack2");
    final PsiDirectory targetDirectory = pack2.getDirectories()[0];
    CopyHandler.doCopy(new PsiElement[]{containingFile}, targetDirectory);

    VirtualFile fileAfter = root.findFileByRelativePath("pack2/Klass.java");
    VirtualFile fileExpected = root.findFileByRelativePath("pack2/Klass.expected.java");

    PlatformTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  public void testMultipleFiles() throws Exception {
    String rootBefore = getRoot();
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    final VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    final VirtualFile first = root.findFileByRelativePath("from/1.txt");
    assertNotNull(first);
    final VirtualFile second = root.findFileByRelativePath("from/2.txt");
    assertNotNull(second);

    final PsiFile firstPsi = myPsiManager.findFile(first);
    final PsiFile secondPsi = myPsiManager.findFile(second);

    assertTrue(CopyHandler.canCopy(new PsiElement[]{firstPsi, secondPsi}));

    final VirtualFile toDir = root.findChild("to");
    assertNotNull(toDir);
    final PsiDirectory targetDirectory = myPsiManager.findDirectory(toDir);

    CopyHandler.doCopy(new PsiElement[]{firstPsi, secondPsi}, targetDirectory);

    assertNotNull(root.findFileByRelativePath("to/1.txt"));
    assertNotNull(root.findFileByRelativePath("to/2.txt"));
  }

  public void testPackageInfo() throws Exception {
    String rootBefore = getRoot();
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    final VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    final VirtualFile first = root.findFileByRelativePath("from/package-info.java");
    assertNotNull(first);
    
    final PsiFile firstPsi = myPsiManager.findFile(first);
    
    assertTrue(CopyHandler.canCopy(new PsiElement[]{firstPsi}));

    final VirtualFile toDir = root.findChild("to");
    assertNotNull(toDir);
    final PsiDirectory targetDirectory = myPsiManager.findDirectory(toDir);

    CopyHandler.doCopy(new PsiElement[]{firstPsi}, targetDirectory);

    final VirtualFile dest = root.findFileByRelativePath("to/package-info.java");
    assertNotNull(dest);

    VirtualFile fileExpected = root.findFileByRelativePath("to/package-info.expected.java");
    
    PlatformTestUtil.assertFilesEqual(fileExpected, dest);
  }
}
