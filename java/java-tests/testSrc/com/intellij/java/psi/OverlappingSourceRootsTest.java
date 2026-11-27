// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class OverlappingSourceRootsTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myProjectRoot;
  private VirtualFile mySourceRoot1;
  private VirtualFile mySourceRoot2;
  private VirtualFile mySourceRoot11;
  private VirtualFile mySourceRoot21;
  private VirtualFile myFile1;
  private VirtualFile myFile2;
  private VirtualFile myFile11;
  private VirtualFile myFile21;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Module myModule = fixture.getModule();
    myProjectRoot = fixture.createTempVirtualDir();
    WriteAction.runAndWait(
      () -> {
        mySourceRoot1 = VfsTestUtil.createDir(myProjectRoot, "root1");
        mySourceRoot2 = VfsTestUtil.createDir(myProjectRoot, "root2");
        mySourceRoot11 = VfsTestUtil.createDir(mySourceRoot1, "root11");
        mySourceRoot21 = VfsTestUtil.createDir(mySourceRoot2, "root21");

        PsiTestUtil.addContentRoot(myModule, myProjectRoot);
        PsiTestUtil.addSourceRoot(myModule, mySourceRoot21);
        PsiTestUtil.addSourceRoot(myModule, mySourceRoot1);
        PsiTestUtil.addSourceRoot(myModule, mySourceRoot2);
        PsiTestUtil.addSourceRoot(myModule, mySourceRoot11);

        myFile1 = VfsTestUtil.createFile(mySourceRoot1, "File1.java");
        myFile2 = VfsTestUtil.createFile(mySourceRoot2, "File2.java");
        myFile11 = VfsTestUtil.createFile(mySourceRoot11, "File11.java");
        myFile21 = VfsTestUtil.createFile(mySourceRoot21, "File21.java");
      }
    );
  }

  @Test
  public void testFindRoot1() {
    ReadAction.runBlocking(this::checkFindRoot);
  }

  @Test
  public void testFindRoot2() {
    ReadAction.runBlocking(this::checkFindRoot);
  }

  private void checkFindRoot() {
    PsiManager myPsiManager = heavyFixture.get().getPsiManager();
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
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(heavyFixture.get().getProject()).getFileIndex().getSourceRootForFile(virtualFile);
    return heavyFixture.get().getPsiManager().findDirectory(sourceRoot);
  }

  @Test
  public void testFindPackage1() {
    ReadAction.runBlocking(this::checkFindPackage);
  }

  @Test
  public void testFindPackage2() {
    ReadAction.runBlocking(this::checkFindPackage);
  }

  private void checkFindPackage() {
    PsiManager myPsiManager = heavyFixture.get().getPsiManager();
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
