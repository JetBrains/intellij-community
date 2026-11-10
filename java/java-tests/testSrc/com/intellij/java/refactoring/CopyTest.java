// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CopyTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/refactoring/copy/multifile/");
  }

  @Test
  public void testCopyAvailable() throws Exception {
    doTest();
  }

  @Test
  public void testJavaAndTxt() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject(myFixture.getTestName(true), "");
    ReadAction.runBlocking(() -> {
      PsiPackage pack1 = myFixture.findPackage("pack1");
      PsiPackage pack2 = myFixture.findPackage("pack2");
      assertTrue(CopyHandler.canCopy(new PsiElement[]{pack1.getDirectories()[0], pack2.getDirectories()[0]}));
    });
  }

  @Test
  public void testMultipleClasses() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile root = myFixture.copyDirectoryToProject(myFixture.getTestName(true), "");
    final PsiClass aClass = ReadAction.computeBlocking(() -> myFixture.findClass("pack1.Klass"));
    
    final PsiFile containingFile = aClass.getContainingFile();

    ReadAction.runBlocking(() -> {
      assertTrue(CopyHandler.canCopy(new PsiElement[]{containingFile}));
      assertFalse(CopyHandler.canClone(new PsiElement[]{containingFile}));
    });

    PsiPackage pack2 = myFixture.findPackage("pack2");
    final PsiDirectory targetDirectory = pack2.getDirectories()[0];
    EdtTestUtil.runInEdtAndWait(() -> {
      CopyHandler.doCopy(new PsiElement[]{containingFile}, targetDirectory);

      VirtualFile fileAfter = root.findFileByRelativePath("pack2/Klass.java");
      VirtualFile fileExpected = root.findFileByRelativePath("pack2/Klass.expected.java");

      PlatformTestUtil.assertFilesEqual(fileExpected, fileAfter);
    });
  }

  @Test
  public void testMultipleFiles() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile root = myFixture.copyDirectoryToProject(myFixture.getTestName(true), "");

    final VirtualFile first = root.findFileByRelativePath("from/1.txt");
    assertNotNull(first);
    final VirtualFile second = root.findFileByRelativePath("from/2.txt");
    assertNotNull(second);
    final VirtualFile java = root.findFileByRelativePath("from/Foo.java");
    assertNotNull(java);

    final PsiFile firstPsi = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(first));
    final PsiFile secondPsi = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(second));
    final PsiFile javaPsi = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(java));

    ReadAction.runBlocking(() -> assertTrue(CopyHandler.canCopy(new PsiElement[]{firstPsi, secondPsi, javaPsi})));

    final VirtualFile toDir = root.findChild("to");
    assertNotNull(toDir);
    final PsiDirectory targetDirectory = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findDirectory(toDir));

    EdtTestUtil.runInEdtAndWait(() -> {
      CopyHandler.doCopy(new PsiElement[]{firstPsi, secondPsi, javaPsi}, targetDirectory);

      assertNotNull(root.findFileByRelativePath("to/1.txt"));
      assertNotNull(root.findFileByRelativePath("to/2.txt"));
      VirtualFile javaFile = root.findFileByRelativePath("to/Foo.java");
      assertNotNull(javaFile);
      PsiFile file = PsiManager.getInstance(myFixture.getProject()).findFile(javaFile);
      assertTrue(file instanceof PsiJavaFile && "to".equals(((PsiJavaFile)file).getPackageName()));
    });
  }

  @Test
  public void testPackageInfo() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile root = myFixture.copyDirectoryToProject(myFixture.getTestName(true), "");
    final VirtualFile first = root.findFileByRelativePath("from/package-info.java");
    assertNotNull(first);
    
    final PsiFile firstPsi = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(first));

    ReadAction.runBlocking(() -> assertTrue(CopyHandler.canCopy(new PsiElement[]{firstPsi})));

    final VirtualFile toDir = root.findChild("to");
    assertNotNull(toDir);
    final PsiDirectory targetDirectory = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findDirectory(toDir));

    EdtTestUtil.runInEdtAndWait(() -> {
      CopyHandler.doCopy(new PsiElement[]{firstPsi}, targetDirectory);

      final VirtualFile dest = root.findFileByRelativePath("to/package-info.java");
      assertNotNull(dest);

      VirtualFile fileExpected = root.findFileByRelativePath("to/package-info.expected.java");
    
      PlatformTestUtil.assertFilesEqual(fileExpected, dest);
    });
  }
}
