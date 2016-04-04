/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class SameSourceRootInTwoModulesTest extends PsiTestCase {
  private VirtualFile myPrjDir1;
  private VirtualFile mySrcDir1;
  private VirtualFile myPackDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile rootVFile =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

          myPrjDir1 = createChildDirectory(rootVFile, "prj1");
          mySrcDir1 = createChildDirectory(myPrjDir1, "src1");

          myPackDir = createChildDirectory(mySrcDir1, "p");
          VirtualFile file1 = createChildData(myPackDir, "A.java");
          setFileText(file1, "package p; public class A{ public void foo(); }");
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

          PsiTestUtil.addContentRoot(myModule, myPrjDir1);
          PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  public void testBug() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiClass psiClass = myJavaFacade.findClass("p.A");
        assertEquals("p.A", psiClass.getQualifiedName());

        final PsiFile psiFile = myPsiManager.findFile(myPackDir.findChild("A.java"));
        psiFile.getChildren();
        assertEquals(psiFile, psiClass.getContainingFile());

        VirtualFile file = psiFile.getVirtualFile();
        assertEquals(myModule, ModuleUtil.findModuleForFile(file, myProject));

        Module anotherModule = createModule("another");
        myFilesToDelete.add(new File(anotherModule.getModuleFilePath()));

        PsiTestUtil.addSourceRoot(anotherModule, mySrcDir1);

        assertEquals(anotherModule, ModuleUtil.findModuleForFile(file, myProject));
      }
    });
  }
}
