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
    VirtualFile root =
      PsiTestUtil.createTestProjectStructure(myProject, myModule, OptimizeImportsTest.BASE_PATH + "/testData", myFilesToDelete, false);
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
}
