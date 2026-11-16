// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.move.moveFilesOrDirectories.JavaMoveFilesOrDirectoriesHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MoveClassAndFileTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClassAndFile/");

  @Test
  public void testAllClassesInFile() {
    doTest("t", "txt2move.txt", "s.MyClass", "s.MyOneMoreClass");
  }

  @Test
  public void testOnlyPackageLocalClass() {
    doTest("t", "txt2move.txt", "s.MyLocal");
  }

  @Test
  public void testClassAndPackageInfo() {
    doTest("t", "package-info.java", "s.MyClass");
  }

  @Test
  public void testLeavePackageLocalClass() {
    doTest("t", "txt2move.txt", "s.MyClass");
  }

  @Test
  public void testNestedClassesInFile() {
    doTest("t", null, "s.MyClass.F1", "s.MyClass.F2");
  }

  @Test
  public void testFileToOwnDirectory() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      var file = myFixture.addFileToProject("s/Own.java", "package s; class Own {}");
      var dir = file.getContainingDirectory();
      MoveFilesOrDirectoriesUtil.doMove(myFixture.getProject(), new PsiElement[]{file}, new PsiElement[]{dir}, null, null);
      assertTrue(file.isValid());
      assertEquals(dir, file.getContainingDirectory());
    });
  }

  @Test
  public void testDirectoryToOwnParent() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      var file = myFixture.addFileToProject("s/own/Own.java", "package s.own; class Own {}");
      var dir = file.getContainingDirectory();
      var parent = dir.getParentDirectory();
      MoveFilesOrDirectoriesUtil.doMove(myFixture.getProject(), new PsiElement[]{dir}, new PsiElement[]{parent}, null, null);
      assertTrue(dir.isValid());
      assertEquals(parent, dir.getParentDirectory());
    });
  }

  private void doTest(String newPackageName, String fileNameNearFirstClass, String... classNames) {
    multiFile.get().doTest(() -> performAction(newPackageName, fileNameNearFirstClass, classNames));
  }

  private void performAction(String newPackageName, String fileName, String... classNames) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final PsiElement[] elements = new PsiElement[classNames.length + (fileName != null ? 1 : 0)];
    for(int i = 0; i < classNames.length; i++){
      String className = classNames[i];
      elements[i] = myFixture.findClass(className);
    }
    if (fileName != null) {
      elements[classNames.length] = elements[0].getContainingFile().getContainingDirectory().findFile(fileName);
    }

    PsiPackage aPackage = myFixture.findPackage(newPackageName);
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertEquals(1, dirs.length);

    final JavaMoveFilesOrDirectoriesHandler handler = new JavaMoveFilesOrDirectoriesHandler();
    assertTrue(handler.canMove(elements, dirs[0], null));
    handler.doMove(myFixture.getProject(), elements, dirs[0], null);
  }
}

