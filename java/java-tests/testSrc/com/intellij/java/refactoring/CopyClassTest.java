// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestIndexingModeSupporter;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class CopyClassTest extends LightMultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/copyClass/";
  }

  public void testReplaceAllOccurrences() throws Exception {
    doTest("Foo", "Bar");
  }

  public void testPrivateMethodsInInterfaces() throws Exception {
    doTest("Foo", "Bar");
  }

  public void testReplaceAllOccurrences1() throws Exception {
    doTest("Foo", "Bar");
  }

  public void testRecursiveTypes() throws Exception {
    doTest("Foo", "Bar");
  }

  public void testConflictInSameFolder() throws Exception {
    assertThrows(RuntimeException.class, "already exist", () -> doTest("Foo", "Foo"));
  }

  public void testLibraryClass() throws Exception {  // IDEADEV-28791
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
    doTest("java.util.ArrayList", "Bar");
  }

  private void doTest(final String oldName, final String copyName) throws Exception {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    performAction(oldName, copyName);

    VirtualFile fileAfter = myFixture.findFileInTempDir(copyName + ".java");
    VirtualFile fileExpected = myFixture.findFileInTempDir(copyName + ".expected.java");

    PlatformTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  private void performAction(final String oldName, final String copyName) throws Exception {
    final PsiClass oldClass = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(() -> myFixture.findClass(oldName));

    WriteCommandAction.writeCommandAction(getProject()).run(
                                             () -> {
                                               PsiDirectory targetDirectory =
                                                 getPsiManager().findDirectory(myFixture.getTempDirFixture().findOrCreateDir(""));
                                               Map<PsiFile, PsiClass[]> sourceClasses =
                                                 Collections.singletonMap(oldClass.getNavigationElement().getContainingFile(),
                                                                          new PsiClass[]{oldClass});
                                               CopyClassesHandler.doCopyClasses(sourceClasses, copyName, targetDirectory, getProject());
                                               PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
                                             });
  }

  public void testPackageLocalClasses() {
    doMultifileTest();
  }

  public void testPackageLocalMethods() {
    doMultifileTest();
  }

  public void testPackageLocalAndExtends() {
    doMultifileTest();
  }

  //copy all classes from p1 -> p2
  private void doMultifileTest() {
    doTest(() -> {
      final HashMap<PsiFile, PsiClass[]> map = new HashMap<>();
      final VirtualFile sourceDir = myFixture.findFileInTempDir("p1");
      for (VirtualFile file : sourceDir.getChildren()) {
        final PsiFile psiFile = getPsiManager().findFile(file);
        if (psiFile instanceof PsiJavaFile) {
          map.put(psiFile, ((PsiJavaFile)psiFile).getClasses());
        }
      }

      final VirtualFile targetVDir = myFixture.findFileInTempDir("p2");
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        CopyClassesHandler.doCopyClasses(map, null, getPsiManager().findDirectory(targetVDir), getProject());
      });
    }, "multifile/" + getTestName(true));
  }

  public void testPackageHierarchy() {
    doPackageCopy();
  }

  public void testPackageOneLevelHierarchy() {
    doPackageCopy();
  }

  private void doPackageCopy() {
    doTest(() -> {
      final PsiDirectory sourceP1Dir = getPsiManager().findDirectory(myFixture.findFileInTempDir("p1"));
      final PsiDirectory targetP2Dir = getPsiManager().findDirectory(myFixture.findFileInTempDir("p2"));
      new CopyClassesHandler().doCopy(new PsiElement[]{sourceP1Dir}, targetP2Dir);
    }, "multifile/" + getTestName(true));
  }

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTestSuite(CopyClassTest.class);
    TestIndexingModeSupporter.addAllTests(CopyClassTest.class, suite);
    return suite;
  }
}
