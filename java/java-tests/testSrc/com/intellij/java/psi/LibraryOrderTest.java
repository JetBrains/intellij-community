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

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *  @author dsl
 */
public class LibraryOrderTest extends PsiTestCase {

  public void test1() {
    setupPaths();
    checkClassFromLib("test.A", "1");

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final OrderEntry[] order = rootModel.getOrderEntries();
    final int length = order.length;
    OrderEntry lib2 = order[length - 1];
    OrderEntry lib1 = order[length - 2];
    assertTrue(lib1 instanceof LibraryOrderEntry);
    assertEquals("lib1", ((LibraryOrderEntry) lib1).getLibraryName());
    assertTrue(lib2 instanceof LibraryOrderEntry);
    assertEquals("lib2", ((LibraryOrderEntry) lib2).getLibraryName());

    order[length - 1] = lib1;
    order[length - 2] = lib2;
    rootModel.rearrangeOrderEntries(order);

    ApplicationManager.getApplication().runWriteAction(() -> rootModel.commit()
    );

    checkClassFromLib("test.A", "2");
  }

  public void testNavigation() {
    setupPaths();
    final PsiClass classA = getJavaFacade().findClass("test.A");
    final PsiElement navigationElement = classA.getNavigationElement();
    assertNotNull(navigationElement);
    assertTrue(navigationElement != classA);
    assertEquals("A.java", navigationElement.getContainingFile().getVirtualFile().getName());
  }

  private void checkClassFromLib(String qualifiedName, String index) {
    final PsiClass classA = (PsiClass)getJavaFacade().findClass(qualifiedName).getNavigationElement();
    assertNotNull(classA);
    final PsiMethod[] methodsA = classA.getMethods();
    assertEquals(1, methodsA.length);
    assertEquals("methodOfClassFromLib" + index, methodsA[0].getName());
  }

  public void setupPaths() {
    final String basePath = JavaTestUtil.getJavaTestDataPath() + "/psi/libraryOrder/";

    final VirtualFile lib1SrcFile = refreshAndFindFile(basePath + "lib1/src");
    final VirtualFile lib1classes = refreshAndFindFile(basePath + "lib1/classes");
    final VirtualFile lib2SrcFile = refreshAndFindFile(basePath + "lib2/src");
    final VirtualFile lib2classes = refreshAndFindFile(basePath + "lib2/classes");

    assertTrue(lib1SrcFile != null);
    assertTrue(lib2SrcFile != null);

    addLibraryWithSourcePath("lib1", lib1classes, lib1SrcFile);
    addLibraryWithSourcePath("lib2", lib2classes, lib2SrcFile);

    final List<VirtualFile> list = Arrays.asList(OrderEnumerator.orderEntries(myModule).getClassesRoots());
    assertTrue(list.contains(lib1classes));
    assertTrue(list.contains(lib2classes));
  }

  private VirtualFile refreshAndFindFile(String path) {
    final File ioLib1Src = new File(path);
    final VirtualFile lib1SrcFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioLib1Src);
    return lib1SrcFile;
  }

  private void addLibraryWithSourcePath(String name, VirtualFile libClasses, final VirtualFile libSource) {
    ModuleRootModificationUtil.addModuleLibrary(myModule, name, Collections.singletonList(libClasses.getUrl()),
                                                Collections.singletonList(libSource.getUrl()));
  }
}
