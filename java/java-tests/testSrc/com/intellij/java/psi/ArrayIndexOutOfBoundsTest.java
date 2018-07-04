// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class ArrayIndexOutOfBoundsTest extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ArrayIndexOutOfBoundsTest");
  private VirtualFile myProjectRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/arrayIndexOutOfBounds/src";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myProjectRoot = createTestProjectStructure(root);
  }

  public void testSCR10930() {
    renamePackage();
    deleteNewPackage();
    restoreSources();
    renamePackage();
  }

  public void testSimplerCase() {
    renamePackage();
    restoreSources();

    PsiFile psiFile = myPsiManager.findFile(myProjectRoot.findFileByRelativePath("bla/Bla.java"));
    assertNotNull(psiFile);

    assertEquals(4, psiFile.getChildren().length);
  }

  public void testLongLivingClassAfterRename() {
    PsiClass psiClass = myJavaFacade.findClass("bla.Bla", GlobalSearchScope.projectScope(getProject()));
    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(psiClass);
    renamePackage();
    //assertTrue(psiClass.isValid());
    SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  private void restoreSources() {
    Runnable runnable = () -> {
      try {
        FileUtil.copyDir(new File(JavaTestUtil.getJavaTestDataPath() + "/psi/arrayIndexOutOfBounds/src"),
                         VfsUtilCore.virtualToIoFile(myProjectRoot));
      }
      catch (IOException e) {
        LOG.error(e);
      }
      VirtualFileManager.getInstance().syncRefresh();
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable,  "", null);
  }

  private void deleteNewPackage() {
    Runnable runnable = () -> {
      final PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage("anotherBla");
      assertNotNull("Package anotherBla not found", aPackage);
      WriteCommandAction.runWriteCommandAction(null, () -> aPackage.getDirectories()[0].delete());
      VirtualFileManager.getInstance().syncRefresh();
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable,  "", null);
  }

  private void renamePackage() {
    Runnable runnable = () -> {
      PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage("bla");
      assertNotNull("Package bla not found", aPackage);

      PsiDirectory dir = aPackage.getDirectories()[0];
      new RenameProcessor(myProject, dir, "anotherBla", true, true).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable,  "", null);
  }
}
