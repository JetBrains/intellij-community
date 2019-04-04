// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.move.moveFilesOrDirectories.JavaMoveFilesOrDirectoriesHandler;

public class MoveClassAndFileTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClassAndFile/";
  }

  public void testAllClassesInFile() {
    doTest("t", "txt2move.txt", "s.MyClass", "s.MyOneMoreClass");
  }

  public void testOnlyPackageLocalClass() {
    doTest("t", "txt2move.txt", "s.MyLocal");
  }

  public void testClassAndPackageInfo() {
    doTest("t", "package-info.java", "s.MyClass");
  }

  public void testLeavePackageLocalClass() {
    doTest("t", "txt2move.txt", "s.MyClass");
  }

  public void testNestedClassesInFile() {
    doTest("t", null, "s.MyClass.F1", "s.MyClass.F2");
  }

  private void doTest(String newPackageName, String fileNameNearFirstClass, String... classNames) {
    doTest(() -> performAction(newPackageName, fileNameNearFirstClass, classNames));
  }

  private void performAction(String newPackageName, String fileName, String... classNames) {
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
    assertTrue(handler.canMove(elements, dirs[0]));
    handler.doMove(getProject(), elements, dirs[0], null);
  }
}

