/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.move.moveFilesOrDirectories.JavaMoveFilesOrDirectoriesHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class MoveClassAndFileTest extends RefactoringTestCase {

  public void testAllClassesInFile() throws Exception {
    doTest("allClassesInFile", "t", "txt2move.txt", "s.MyClass", "s.MyOneMoreClass");
  }

  public void testOnlyPackageLocalClass() throws Exception {
    doTest("onlyPackageLocalClass", "t", "txt2move.txt", "s.MyLocal");
  }

  public void testLeavePackageLocalClass() throws Exception {
    doTest("leavePackageLocalClass", "t", "txt2move.txt", "s.MyClass");
  }

  public void testNestedClassesInFile() throws Exception {
    doTest("nestedClassesInFile", "t", null, "s.MyClass.F1", "s.MyClass.F2");
  }

  private void doTest(String testName, String newPackageName, String fileNameNearFirstClass, String... classNames) throws Exception {
    String root = JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClassAndFile/" + testName;

    String rootBefore = root + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    performAction(newPackageName, fileNameNearFirstClass, classNames);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }

  private void performAction(String newPackageName, String fileName, String... classNames) {
    final PsiElement[] elements = new PsiElement[classNames.length + (fileName != null ? 1 : 0)];
    for(int i = 0; i < classNames.length; i++){
      String className = classNames[i];
      elements[i] = myJavaFacade.findClass(className, GlobalSearchScope.projectScope(getProject()));
      assertNotNull("Class " + className + " not found", elements[i]);
    }
    if (fileName != null) {
      elements[classNames.length] = elements[0].getContainingFile().getContainingDirectory().findFile(fileName);
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertEquals(1, dirs.length);

    final JavaMoveFilesOrDirectoriesHandler handler = new JavaMoveFilesOrDirectoriesHandler();
    assertTrue(handler.canMove(elements, dirs[0]));
    handler.doMove(getProject(), elements, dirs[0], null);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}

