// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Heavy weight: creates project for each test method. Consider using {@link LightMultiFileTestCase} instead
 */
public abstract class MultiFileTestCase extends JavaCodeInsightTestCase {
  protected boolean myDoCompare = true;

  protected void doTest(final PerformAction performAction) {
    doTest(performAction, getTestName(true));
  }

  protected void doTest(final PerformAction performAction, final boolean lowercaseFirstLetter) {
    doTest(performAction, getTestName(lowercaseFirstLetter));
  }

  protected void doTest(final PerformAction performAction, final String testName) {
    try {
      String path = getTestDataPath() + getTestRoot() + testName;

      String pathBefore = path + "/before";
      VirtualFile rootDir = createTestProjectStructure(pathBefore, false);
      prepareProject(rootDir);
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      String pathAfter = path + "/after";
      final VirtualFile rootAfter = LocalFileSystem.getInstance().findFileByPath(pathAfter.replace(File.separatorChar, '/'));

      performAction.performAction(rootDir, rootAfter);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting());

      FileDocumentManager.getInstance().saveAllDocuments();

      if (myDoCompare) {
        compareResults(rootAfter, rootDir);
      }
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void compareResults(VirtualFile rootAfter, VirtualFile rootDir) throws IOException {
    PlatformTestUtil.assertDirectoriesEqual(rootAfter, rootDir);
  }

  protected void prepareProject(VirtualFile rootDir) {
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
  }

  @NotNull
  @Override
  @NonNls
  protected abstract String getTestRoot();

  protected interface PerformAction {
    void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception;
  }
}
