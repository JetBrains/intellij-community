/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.refactoring.rename.RenamePsiPackageProcessor;
import com.intellij.testFramework.PsiTestUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;

public class MovePackageAsDirectoryTest extends MultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/movePackageAsDir/";
  }

  public void testMovePackage() {
    doTest(createAction("pack1", "target"));
  }

  public void testRenamePackage() {
    final PerformAction action = (rootDir, rootAfter) -> {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      final PsiPackage sourcePackage = psiFacade.findPackage("pack1");
      assertNotNull(sourcePackage);

      RenamePsiPackageProcessor.createRenameMoveProcessor("pack1.pack2", sourcePackage, false, false).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    };
    doTest(action);
  }

  public void testRenamePackageUp() {
    final PerformAction action = (rootDir, rootAfter) -> {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      final PsiPackage sourcePackage = psiFacade.findPackage("pack1.pack2");
      assertNotNull(sourcePackage);

      RenamePsiPackageProcessor.createRenameMoveProcessor("pack1", sourcePackage, false, false).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    };
    doTest(action);
  }

  public void testRenamePackageStaticImportsToNestedClasses() {
    final PerformAction action = (rootDir, rootAfter) -> {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      final PsiPackage sourcePackage = psiFacade.findPackage("pack1.pack2");
      assertNotNull(sourcePackage);

      RenamePsiPackageProcessor.createRenameMoveProcessor("pack0.pack2", sourcePackage, false, false).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    };
    doTest(action);
  }

  public void testMovePackageWithTxtFilesInside() {
    doTest(createAction("pack1", "target"));
  }

  public void testMultipleClassesInOneFile() {
    final boolean [] fileWasDeleted = new boolean[]{false};
    final VirtualFileListener fileAdapter = new VirtualFileListener() {
      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        fileWasDeleted[0] = !event.getFile().isDirectory();
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(fileAdapter);
    try {
      doTest(createAction("pack1", "target"));
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(fileAdapter);
    }
    Assert.assertFalse("Deleted instead of moved", fileWasDeleted[0]);
  }


  public void testRemoveUnresolvedImports() {
    doTest(createAction("pack1", "target"));
  }

  public void testXmlDirRefs() {
    doTest(createAction("pack1", "target"));
  }

  private static final String EMPTY_TXT = "empty.txt";
  public void testXmlEmptyDirRefs() {
    final String packageName = "pack1";
    doTest(new MyPerformAction(packageName, "target"){
      @Override
      protected void preprocessSrcDir(PsiDirectory srcDirectory) {
        final PsiFile empty = srcDirectory.findFile(EMPTY_TXT);
        assert empty != null;
        WriteCommandAction.runWriteCommandAction(null, empty::delete);
      }

      @Override
      protected void postProcessTargetDir(PsiDirectory targetDirectory) {
        final PsiDirectory subdirectory = targetDirectory.findSubdirectory(packageName);
        assert subdirectory != null;
        ApplicationManager.getApplication().runWriteAction(() -> {
          subdirectory.createFile(EMPTY_TXT);
        });
      }
    });
  }

  public void testEmptySubDirs() {
    final String packageName = "pack1";
    doTest(new MyPerformAction(packageName, "target"){
      private static final String FOO = "pack1.subPack.Foo";
      @Override
      protected void preprocessSrcDir(PsiDirectory srcDirectory) {
        final PsiClass empty = JavaPsiFacade.getInstance(getProject()).findClass(FOO, GlobalSearchScope.projectScope(getProject()));
        assert empty != null;
        ApplicationManager.getApplication().runWriteAction(empty::delete);
      }

      @Override
      protected void postProcessTargetDir(PsiDirectory targetDirectory) {
       final PsiDirectory subdirectory = targetDirectory.findSubdirectory(packageName);
        assert subdirectory != null;
        final PsiDirectory emptyDir = subdirectory.findSubdirectory("subPack");
        assert emptyDir != null;
        ApplicationManager.getApplication().runWriteAction(() -> {
          emptyDir.createFile(EMPTY_TXT);
        });
      }
    });
  }

  private MyPerformAction createAction(final String packageName, final String targetPackageName) {
    return new MyPerformAction(packageName, targetPackageName);
  }

  @Override
  protected void prepareProject(VirtualFile rootDir) {
    PsiTestUtil.addContentRoot(myModule, rootDir);
    final VirtualFile[] children = rootDir.getChildren();
    for (VirtualFile child : children) {
      if (child.getName().startsWith("src")) {
        PsiTestUtil.addSourceRoot(myModule, child);
      }
    }
  }

  private class MyPerformAction implements PerformAction {
    private final String myPackageName;
    private final String myTargetPackageName;

    public MyPerformAction(String packageName, String targetPackageName) {
      myPackageName = packageName;
      myTargetPackageName = targetPackageName;
    }

    @Override
    public void performAction(VirtualFile rootDir, VirtualFile rootAfter) {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      final Comparator<PsiDirectory> directoryComparator = Comparator.comparing(o -> o.getVirtualFile().getPresentableUrl());

      final PsiPackage sourcePackage = psiFacade.findPackage(myPackageName);
      assertNotNull(sourcePackage);
      final PsiDirectory[] srcDirectories = sourcePackage.getDirectories();
      assertEquals(srcDirectories.length, 2);
      Arrays.sort(srcDirectories, directoryComparator);

      final PsiPackage targetPackage = psiFacade.findPackage(myTargetPackageName);
      assertNotNull(targetPackage);
      final PsiDirectory[] targetDirectories = targetPackage.getDirectories();
      Arrays.sort(targetDirectories, directoryComparator);
      assertTrue(targetDirectories.length > 0);
      preprocessSrcDir(srcDirectories[0]);
      new MoveDirectoryWithClassesProcessor(getProject(), new PsiDirectory[]{srcDirectories[0]}, targetDirectories[0], false, false, true, null).run();
      postProcessTargetDir(targetDirectories[0]);
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    protected void postProcessTargetDir(PsiDirectory targetDirectory) {
    }

    protected void preprocessSrcDir(PsiDirectory srcDirectory) {
    }
  }
}
