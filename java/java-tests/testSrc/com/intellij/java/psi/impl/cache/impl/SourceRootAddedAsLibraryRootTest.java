// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class SourceRootAddedAsLibraryRootTest extends JavaPsiTestCase {
  private VirtualFile myDir;
  private VirtualFile myVFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();
    VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

    myDir = createChildDirectory(rootVFile, "contentAndLibrary");

    PsiTestUtil.addSourceRoot(myModule, myDir);
  }

  private void changeRoots() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, myDir.getUrl());
  }

  public void testBug() {
    touchFileSync();
    PsiFile psiFile = myPsiManager.findFile(myVFile);
    psiFile.getText();
    changeRoots();
  }

  private void touchFileSync() {
    myVFile = createChildData(myDir, "A.java");
    setFileText(myVFile, "package p; public class A{ public void foo(); }");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }
}
