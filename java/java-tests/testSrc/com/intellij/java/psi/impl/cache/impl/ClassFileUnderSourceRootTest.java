// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class ClassFileUnderSourceRootTest extends JavaProjectTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    File dir = createTempDirectory();
    final VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(dir);
    assertNotNull(root);

    FileUtil.writeToFile(new File(dir, "p/A.java"), "package p;\npublic class A { }");
    FileUtil.copy(new File(PathManagerEx.getTestDataPath() + "/psi/cls/repo/pack/MyClass.class"), new File(dir, "pack/MyClass.class"));

    root.refresh(false, true);
    assertSize(2, root.getChildren());

    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiTestUtil.addSourceRoot(myModule, root);
      ModuleRootModificationUtil.addModuleLibrary(myModule, root.getUrl());
    });
  }

  public void testFindClasses() {
    PsiClass srcClass = myJavaFacade.findClass("p.A");
    assertNotNull(srcClass);
    assertEquals("p.A", srcClass.getQualifiedName());

    PsiClass pkgClass = myJavaFacade.findClass("pack.MyClass");
    assertNotNull(pkgClass);
    assertEquals("pack.MyClass", pkgClass.getQualifiedName());
  }
}
