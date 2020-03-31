// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

public class OverlappingSourceRootsTest extends JavaPsiTestCase {
  private static final Logger LOG = Logger.getInstance(OverlappingSourceRootsTest.class);

  private VirtualFile myProjectRoot;
  private VirtualFile mySourceRoot1;
  private VirtualFile mySourceRoot2;
  private VirtualFile mySourceRoot11;
  private VirtualFile mySourceRoot21;
  private VirtualFile myFile1;
  private VirtualFile myFile2;
  private VirtualFile myFile11;
  private VirtualFile myFile21;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      () -> {

        try {
          File dir = createTempDirectory();

          myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));

          mySourceRoot1 = createChildDirectory(myProjectRoot, "root1");
          mySourceRoot2 = createChildDirectory(myProjectRoot, "root2");
          mySourceRoot11 = createChildDirectory(mySourceRoot1, "root11");
          mySourceRoot21 = createChildDirectory(mySourceRoot2, "root21");

          PsiTestUtil.addContentRoot(myModule, myProjectRoot);
          PsiTestUtil.addSourceRoot(myModule, mySourceRoot21);
          PsiTestUtil.addSourceRoot(myModule, mySourceRoot1);
          PsiTestUtil.addSourceRoot(myModule, mySourceRoot2);
          PsiTestUtil.addSourceRoot(myModule, mySourceRoot11);

          myFile1 = createChildData(mySourceRoot1, "File1.java");
          myFile2 = createChildData(mySourceRoot2, "File2.java");
          myFile11 = createChildData(mySourceRoot11, "File11.java");
          myFile21 = createChildData(mySourceRoot21, "File21.java");
        } catch (IOException e) {
          LOG.error(e);
        }
      }
    );
  }

  public void testFindRoot1() {
    checkFindRoot();
  }

  public void testFindRoot2() {
    checkFindRoot();
  }

  private void checkFindRoot() {
    PsiDirectory psiDir1 = myPsiManager.findDirectory(mySourceRoot1);
    PsiDirectory psiDir2 = myPsiManager.findDirectory(mySourceRoot2);
    PsiDirectory psiDir11 = myPsiManager.findDirectory(mySourceRoot11);
    PsiDirectory psiDir21 = myPsiManager.findDirectory(mySourceRoot21);

    assertEquals(psiDir1, findSourceRootDirectory(psiDir1));
    assertEquals(psiDir2, findSourceRootDirectory(psiDir2));
    assertEquals(psiDir11, findSourceRootDirectory(psiDir11));
    assertEquals(psiDir21, findSourceRootDirectory(psiDir21));

    PsiFile psiFile1 = myPsiManager.findFile(myFile1);
    PsiFile psiFile2 = myPsiManager.findFile(myFile2);
    PsiFile psiFile11 = myPsiManager.findFile(myFile11);
    PsiFile psiFile21 = myPsiManager.findFile(myFile21);

    assertEquals(psiDir1, findSourceRootDirectory(psiFile1));
    assertEquals(psiDir2, findSourceRootDirectory(psiFile2));
    assertEquals(psiDir11, findSourceRootDirectory(psiFile11));
    assertEquals(psiDir21, findSourceRootDirectory(psiFile21));
  }

  private PsiDirectory findSourceRootDirectory(PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(virtualFile);
    return myPsiManager.findDirectory(sourceRoot);
  }

  public void testFindPackage1() {
    checkFindPackage();
  }

  public void testFindPackage2() {
    checkFindPackage();
  }

  private void checkFindPackage() {
    PsiDirectory psiDir1 = myPsiManager.findDirectory(mySourceRoot1);
    PsiDirectory psiDir2 = myPsiManager.findDirectory(mySourceRoot2);
    PsiDirectory psiDir11 = myPsiManager.findDirectory(mySourceRoot11);
    PsiDirectory psiDir21 = myPsiManager.findDirectory(mySourceRoot21);

    String pack1 = JavaDirectoryService.getInstance().getPackage(psiDir1).getQualifiedName();
    String pack2 = JavaDirectoryService.getInstance().getPackage(psiDir2).getQualifiedName();
    String pack11 = JavaDirectoryService.getInstance().getPackage(psiDir11).getQualifiedName();
    String pack21 = JavaDirectoryService.getInstance().getPackage(psiDir21).getQualifiedName();

    assertEquals("", pack1);
    assertEquals("", pack2);
    assertEquals("", pack11);
    assertEquals("", pack21);
  }
}
