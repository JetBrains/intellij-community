// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class SameSourceRootInTwoModulesTest extends JavaPsiTestCase {
  private VirtualFile myPrjDir1;
  private VirtualFile mySrcDir1;
  private VirtualFile myPackDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile rootVFile =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

      myPrjDir1 = createChildDirectory(rootVFile, "prj1");
      mySrcDir1 = createChildDirectory(myPrjDir1, "src1");

      myPackDir = createChildDirectory(mySrcDir1, "p");
      VirtualFile file1 = createChildData(myPackDir, "A.java");
      setFileText(file1, "package p; public class A{ public void foo(); }");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      PsiTestUtil.addContentRoot(myModule, myPrjDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
    });
  }

  public void testBug() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiClass psiClass = myJavaFacade.findClass("p.A");
      assertEquals("p.A", psiClass.getQualifiedName());

      PsiFile psiFile = myPsiManager.findFile(myPackDir.findChild("A.java"));
      psiFile.getChildren();
      assertEquals(psiFile, psiClass.getContainingFile());

      VirtualFile file = psiFile.getVirtualFile();
      assertEquals(myModule, ModuleUtilCore.findModuleForFile(file, myProject));

      Module anotherModule = createModule("another");

      PsiTestUtil.addSourceRoot(anotherModule, mySrcDir1);

      assertEquals(anotherModule, ModuleUtilCore.findModuleForFile(file, myProject));
    });
  }
}
