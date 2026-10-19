// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class RootsTest extends JavaPsiTestCase {
  public void testTest1() {
    final String rootPath = JavaTestUtil.getJavaTestDataPath() + "/moduleRootManager/roots/" + "test1";
    final VirtualFile[] rootFileBox = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      rootFileBox[0] =
      StandardFileSystems.local().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/'));
    });
    final VirtualFile rootFile = rootFileBox[0];
    final VirtualFile classesFile = rootFile.findChild("classes");
    assertNotNull(classesFile);
    final VirtualFile childOfContent = rootFile.findChild("x.txt");
    assertNotNull(childOfContent);
    final VirtualFile childOfClasses = classesFile.findChild("y.txt");
    assertNotNull(childOfClasses);

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);


    PsiTestUtil.addContentRoot(myModule, rootFile);
    PsiTestUtil.setCompilerOutputPath(myModule, classesFile.getUrl(), false);
    PsiTestUtil.setExcludeCompileOutput(myModule, false);
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertTrue(rootManager.getFileIndex().isInContent(childOfClasses));

    PsiTestUtil.setExcludeCompileOutput(myModule, true);
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertFalse(rootManager.getFileIndex().isInContent(childOfClasses));
  }

}
