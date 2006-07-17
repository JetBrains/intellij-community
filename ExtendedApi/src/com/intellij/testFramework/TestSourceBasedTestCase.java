/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NonNls;

import java.io.File;

@SuppressWarnings({"HardCodedStringLiteral", "ConstantConditions", "JUnitTestCaseInProductSource"})
@NonNls public abstract class TestSourceBasedTestCase extends IdeaTestCase {
  private File myTempDirectory;

  protected void setUp() throws Exception {
    super.setUp();
    myTempDirectory = FileUtil.createTempDirectory("testCopy", "test");
    myFilesToDelete.add(getTestContentFile());
    final File testRoot = new File(PathManagerEx.getTestDataPath(), getTestPath());
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestDirectoryName());
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    FileUtil.copyDir(currentTestRoot, new File(myTempDirectory, getTestDirectoryName()));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                                                             public void run() {
                                                               setupContentRoot();
                                                             }
                                                           });

  }

  protected abstract String getTestPath();

  private File getTestContentFile() {
    return new File(myTempDirectory, getTestDirectoryName());
  }

  private void setupContentRoot() {
    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    ContentEntry contentEntry = modifiableModel.addContentEntry(getContentRoot());
    VirtualFile src = getContentRoot().findChild("src");
    if (src != null) {
      contentEntry.addSourceFolder(src, false);
    }
    modifiableModel.commit();
  }

  protected VirtualFile getContentRoot() {
    File file = getTestContentFile();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  protected String getTestDirectoryName() {
    return getTestName(true);
  }


  protected PsiDirectory getPackageDirectory(final String packageRelativePath) {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src/" + packageRelativePath));
  }

  protected PsiDirectory getSrcDirectory() {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src"));
  }

  protected PsiDirectory getContentDirectory() {
    return getPsiManager().findDirectory(getContentRoot());
  }
}
