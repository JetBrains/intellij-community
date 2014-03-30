/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.util.Arrays;

/**
 * @author dsl
 */
public class ProjectLibrariesTest extends IdeaTestCase {
  public void test() {
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    Library lib = WriteCommandAction.runWriteCommandAction(null, new Computable<Library>() {
      @Override
      public Library compute() {
        return libraryTable.createLibrary("LIB");
      }
    });
    ModuleRootModificationUtil.addDependency(myModule, lib);
    final JavaPsiFacade manager = getJavaFacade();
    assertNull(manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));
    final File file = new File(PathManagerEx.getTestDataPath() + "/psi/repositoryUse/cls");
    final VirtualFile root = WriteCommandAction.runWriteCommandAction(null, new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      }
    });
    assertNotNull(root);
    final Library.ModifiableModel modifyableModel = lib.getModifiableModel();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifyableModel.addRoot(root, OrderRootType.CLASSES);
        modifyableModel.commit();
      }
    });
    final PsiClass aClass = manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(aClass);
  }

  public void test1() {
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    Library lib = WriteCommandAction.runWriteCommandAction(null, new Computable<Library>() {
      @Override
      public Library compute() {
        return libraryTable.createLibrary("LIB");
      }
    });
    ModuleRootModificationUtil.addDependency(myModule, lib);
    final JavaPsiFacade manager = getJavaFacade();
    assertNull(manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));

    final ModifiableRootModel rootModel2 = ModuleRootManager.getInstance(myModule).getModifiableModel();
    assertNotNull(rootModel2.findLibraryOrderEntry(lib));
    final File file = new File(PathManagerEx.getTestDataPath() + "/psi/repositoryUse/cls");
    final VirtualFile root = WriteCommandAction.runWriteCommandAction(null, new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      }
    });
    assertNotNull(root);
    final Library.ModifiableModel modifyableModel = lib.getModifiableModel();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifyableModel.addRoot(root, OrderRootType.CLASSES);
        modifyableModel.commit();
      }
    });
    final PsiClass aClass = manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(aClass);
    assertTrue(Arrays.asList(rootModel2.orderEntries().librariesOnly().classes().getRoots()).contains(root));
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootModel2.commit();
      }
    });
    final PsiClass aClass1 = manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(aClass1);
  }
}
