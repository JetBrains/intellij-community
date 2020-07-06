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
package com.intellij.java.psi;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
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
      new OptimizeImportsProcessor(getProject(), directory, true).run();
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      ApplicationManager.getApplication().saveAll();
    });
    String textAfter = VfsUtilCore.loadText(x);
    assertEquals(textBefore, textAfter);
  }

  public void testOptimizeImportsMustAddUnambiguousImportsIfTheCorrespondingSettingIsOn() throws Exception {
    boolean importsOnTheFly = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    try {
      VirtualFile root = createTestProjectStructure(OptimizeImportsTest.BASE_PATH + "/src1", false);
      PsiTestUtil.addSourceRoot(getModule(), root);
      PsiDirectory directory = myPsiManager.findDirectory(root);
      assertNotNull(directory);
      new OptimizeImportsProcessor(getProject(), directory, true).run();
      WriteCommandAction.runWriteCommandAction(null, () -> {
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        ApplicationManager.getApplication().saveAll();
      });
      String text1After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X1.java"));
      assertTrue(text1After, text1After.contains("import java.util.ArrayList;"));
      String text2After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X2.java"));
      assertTrue(text2After, text2After.contains("import java.util.ArrayList;"));
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = importsOnTheFly;
    }
  }
  public void testOptimizeImportsMustNotAddUnambiguousImportsIfTheCorrespondingSettingIsOff() throws Exception {
    boolean importsOnTheFly = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
    try {
      VirtualFile root = createTestProjectStructure(OptimizeImportsTest.BASE_PATH + "/src1", false);
      PsiTestUtil.addSourceRoot(getModule(), root);
      PsiDirectory directory = myPsiManager.findDirectory(root);
      assertNotNull(directory);
      new OptimizeImportsProcessor(getProject(), directory, true).run();
      WriteCommandAction.runWriteCommandAction(null, () -> {
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        ApplicationManager.getApplication().saveAll();
      });
      String text1After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X1.java"));
      assertFalse(text1After, text1After.contains("import java.util.ArrayList;"));
      String text2After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X2.java"));
      assertFalse(text2After, text2After.contains("import java.util.ArrayList;"));
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = importsOnTheFly;
    }
  }
}
