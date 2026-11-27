// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ArrayIndexOutOfBoundsTest {
  private static final Logger LOG = Logger.getInstance(ArrayIndexOutOfBoundsTest.class);

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myProjectRoot;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/arrayIndexOutOfBounds/src";
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    myProjectRoot = fixture.createTestProjectStructure(root);
  }

  @Test
  public void testSCR10930() {
    renamePackage();
    deleteNewPackage();
    restoreSources();
    renamePackage();
  }

  @Test
  public void testSimplerCase() {
    renamePackage();
    restoreSources();

    PsiFile psiFile = ReadAction.computeBlocking(
      () -> heavyFixture.get().getPsiManager().findFile(myProjectRoot.findFileByRelativePath("bla/Bla.java")));
    assertNotNull(psiFile);

    assertEquals(4, ReadAction.computeBlocking(() -> psiFile.getChildren().length));
  }

  @Test
  public void testLongLivingClassAfterRename() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    ASTNode treeElement = ReadAction.computeBlocking(() -> {
      PsiClass psiClass = fixture.getJavaFacade().findClass("bla.Bla", GlobalSearchScope.projectScope(fixture.getProject()));
      return SourceTreeToPsiMap.psiElementToTree(psiClass);
    });
    renamePackage();
    //assertTrue(psiClass.isValid());
    ReadAction.runBlocking(() -> SourceTreeToPsiMap.treeElementToPsi(treeElement));
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
      VfsTestUtil.syncRefresh();
    };
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(heavyFixture.get().getProject(), runnable, "", null));
  }

  private void deleteNewPackage() {
    Project project = heavyFixture.get().getProject();
    Runnable runnable = () -> {
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("anotherBla");
      assertNotNull(aPackage, "Package anotherBla not found");
      WriteCommandAction.runWriteCommandAction(null, () -> aPackage.getDirectories()[0].delete());
      VfsTestUtil.syncRefresh();
    };
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, runnable, "", null));
  }

  private void renamePackage() {
    Project project = heavyFixture.get().getProject();
    Runnable runnable = () -> {
      PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("bla");
      assertNotNull(aPackage, "Package bla not found");

      PsiDirectory dir = aPackage.getDirectories()[0];
      new RenameProcessor(project, dir, "anotherBla", true, true).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    };
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, runnable, "", null));
  }
}
