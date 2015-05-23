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
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

/**
 * @author max
 */
public class ClassFileUnderSourceRootTest extends IdeaTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    File dir = createTempDirectory();
    final VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(dir);
    assertNotNull(root);

    FileUtil.writeToFile(new File(dir, "p/A.java"), "package p;\npublic class A { }");
    FileUtil.copy(new File(PathManagerEx.getTestDataPath() + "/psi/cls/repo/pack/MyClass.class"), new File(dir, "pack/MyClass.class"));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiTestUtil.addSourceRoot(myModule, root);
        ModuleRootModificationUtil.addModuleLibrary(myModule, root.getUrl());
      }
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
