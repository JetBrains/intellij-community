// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

@HeavyPlatformTestCase.WrapInCommand
public class OptimizeImportsMultiFileTest extends JavaPsiTestCase {
  public void testOptimizeImportInPackageIgnoresFilesOutsideSourceRootsForExampleTestData() throws Exception {
    VirtualFile root = createTestProjectStructure(OptimizeImportsTest.BASE_PATH + "/testData", false);
    ModuleRootModificationUtil.addContentRoot(getModule(), root);
    VirtualFile x = root.findChild("X.java");
    String textBefore = VfsUtilCore.loadText(x);
    PsiDirectory directory = myPsiManager.findDirectory(root);
    assertNotNull(directory);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      new OptimizeImportsProcessor(getProject(), directory, true, false).run();
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    FileDocumentManager.getInstance().saveAllDocuments();
    String textAfter = VfsUtilCore.loadText(x);
    assertEquals(textBefore, textAfter);
  }

  public void testOptimizeImportsMustAddUnambiguousImportsIfTheCorrespondingSettingIsOn() throws Exception {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      VirtualFile root = createTestProjectStructure(OptimizeImportsTest.BASE_PATH + "/src1", false);
      PsiTestUtil.addSourceRoot(getModule(), root);
      PsiDirectory directory = myPsiManager.findDirectory(root);
      assertNotNull(directory);
      new OptimizeImportsProcessor(getProject(), directory, true, false).run();
      WriteCommandAction.runWriteCommandAction(null, () -> {
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      FileDocumentManager.getInstance().saveAllDocuments();
      String text1After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X1.java"));
      assertTrue(text1After, text1After.contains("import java.util.ArrayList;"));
      String text2After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X2.java"));
      assertTrue(text2After, text2After.contains("import java.util.ArrayList;"));
      return null;
    });
  }

  public void testOptimizeImportsMustNotAddUnambiguousImportsIfTheCorrespondingSettingIsOff() throws Exception {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
      VirtualFile root = createTestProjectStructure(OptimizeImportsTest.BASE_PATH + "/src1", false);
      PsiTestUtil.addSourceRoot(getModule(), root);
      PsiDirectory directory = myPsiManager.findDirectory(root);
      assertNotNull(directory);
      new OptimizeImportsProcessor(getProject(), directory, true, false).run();
      WriteCommandAction.runWriteCommandAction(null, () -> {
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      FileDocumentManager.getInstance().saveAllDocuments();
      String text1After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X1.java"));
      assertFalse(text1After, text1After.contains("import java.util.ArrayList;"));
      String text2After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X2.java"));
      assertFalse(text2After, text2After.contains("import java.util.ArrayList;"));
      return null;
    });
  }
}
