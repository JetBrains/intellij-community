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
package com.intellij.java.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.JavaProjectTestCase;

import java.util.Arrays;

public class ProjectLibrariesTest extends JavaProjectTestCase {
  private VirtualFile myRoot;
  private Library myLib;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRoot = LocalFileSystem.getInstance().findFileByPath(PathManagerEx.getTestDataPath() + "/psi/cls/repo");
    assertNotNull(myRoot);

    myLib = WriteCommandAction.runWriteCommandAction(null,
                                                     (Computable<Library>)() -> LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary("LIB"));
    ModuleRootModificationUtil.addDependency(myModule, myLib);
  }

  @Override
  protected void tearDown() throws Exception {
    myLib = null;
    super.tearDown();
  }

  public void test() {
    assertNull(getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));

    final Library.ModifiableModel model = myLib.getModifiableModel();
    ApplicationManager.getApplication().runWriteAction(() -> {
      model.addRoot(myRoot, OrderRootType.CLASSES);
      model.commit();
    });

    assertNotNull(getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));
  }

  public void testNestedModelUpdate() {
    assertNull(getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));

    final ModifiableRootModel moduleModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    try {
      assertNotNull(moduleModel.findLibraryOrderEntry(myLib));

      final Library.ModifiableModel libModel = myLib.getModifiableModel();
      ApplicationManager.getApplication().runWriteAction(() -> {
        libModel.addRoot(myRoot, OrderRootType.CLASSES);
        libModel.commit();
      });

      assertNotNull(getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));
      assertTrue(Arrays.asList(moduleModel.orderEntries().librariesOnly().classes().getRoots()).contains(myRoot));
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> moduleModel.commit());
    }

    assertNotNull(getJavaFacade().findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));
  }
}
