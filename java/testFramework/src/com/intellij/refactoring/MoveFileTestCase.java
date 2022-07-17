// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;

public abstract class MoveFileTestCase extends LightMultiFileTestCase {
  protected void doTest(String targetDirName, String fileToMove) {
    doTest(() -> {
      VirtualFile child = myFixture.findFileInTempDir(fileToMove);
      assertNotNull("File " + fileToMove + " not found", child);
      PsiFile file = getPsiManager().findFile(child);
      assertNotNull(file);

      VirtualFile child1 = myFixture.findFileInTempDir(targetDirName);
      assertNotNull("File " + targetDirName + " not found", child1);
      PsiDirectory targetDirectory = getPsiManager().findDirectory(child1);
      assertNotNull(targetDirectory);

      new MoveFilesOrDirectoriesProcessor(getProject(), new PsiElement[]{file}, targetDirectory, false, false, null, null).run();

      int index = fileToMove.lastIndexOf("/");
      PsiFile psiFile = targetDirectory.findFile(index == -1 ? fileToMove : fileToMove.substring(index + 1));
      assertNotNull(psiFile);
    });
  }
}
