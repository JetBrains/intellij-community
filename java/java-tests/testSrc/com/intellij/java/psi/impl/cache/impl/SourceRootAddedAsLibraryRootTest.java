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
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

/**
 * @author max
 */
public class SourceRootAddedAsLibraryRootTest extends PsiTestCase {
  private VirtualFile myDir;
  private VirtualFile myVFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();
    VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

    myDir = createChildDirectory(rootVFile, "contentAndLibrary");

    PsiTestUtil.addSourceRoot(myModule, myDir);
  }

  private void changeRoots() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, myDir.getUrl());
  }

  public void testBug() {
    touchFileSync();
    PsiFile psiFile = myPsiManager.findFile(myVFile);
    psiFile.getText();
    changeRoots();
  }

  private void touchFileSync() {
    myVFile = createChildData(myDir, "A.java");
    setFileText(myVFile, "package p; public class A{ public void foo(); }");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }
}
