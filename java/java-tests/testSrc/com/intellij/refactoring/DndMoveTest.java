package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Aug 31, 2010
 */
public class DndMoveTest extends CodeInsightTestCase {
  protected String getTestRoot() {
    return "/refactoring/dndMove/";
  }

  public void testPublicJavaClass() throws Exception {
    doTest("d", new Computable<PsiElement>() {
      @Nullable
      @Override
      public PsiElement compute() {
        return JavaPsiFacade.getInstance(getProject()).findClass("d.MyClass");
      }
    }, true);
  }

  public void testSecondJavaClass() throws Exception {
    doTest("d", new Computable<PsiElement>() {
      @Nullable
      @Override
      public PsiElement compute() {
        return JavaPsiFacade.getInstance(getProject()).findClass("d.Second");
      }
    }, false);
  }


  private void doTest(final String targetDirName, final Computable<PsiElement> source, final boolean expected) throws Exception {
    String testName = getTestName(true);
    String root = getTestDataPath() + getTestRoot() + testName;
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final VirtualFile child1 = rootDir.findChild(targetDirName);
    assertNotNull("File " + targetDirName + " not found", child1);
    final PsiDirectory targetDirectory = myPsiManager.findDirectory(child1);
    assertEquals(expected, MoveHandler.isMoveRedundant(source.compute(), targetDirectory));
  }
}