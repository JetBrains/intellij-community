/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.roots.libraries;

import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.classpath.CreateModuleLibraryChooser;
import com.intellij.roots.ModuleRootManagerTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class CreateModuleLibraryFromFilesTest extends ModuleRootManagerTestCase {
  private LibraryTable.ModifiableModel myModifiableModel;
  private ModifiableRootModel myModifiableRootModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    myModifiableModel = myModifiableRootModel.getModuleLibraryTable().getModifiableModel();
  }

  public void testSingleJar() {
    Library library = assertOneElement(createLibraries(new OrderRoot(getJDomJar(), OrderRootType.CLASSES)));
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getJDomJar());
    assertEmpty(library.getFiles(OrderRootType.SOURCES));
  }

  public void testTwoJars() {
    List<Library> libraries = createLibraries(new OrderRoot(getJDomJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getAsmJar(), OrderRootType.CLASSES));
    assertEquals(2, libraries.size());
    assertNull(libraries.get(0).getName());
    assertSameElements(libraries.get(0).getFiles(OrderRootType.CLASSES), getJDomJar());
    assertNull(libraries.get(1).getName());
    assertSameElements(libraries.get(1).getFiles(OrderRootType.CLASSES), getAsmJar());
  }

  public void testJarAndSources() {
    Library library = assertOneElement(createLibraries(new OrderRoot(getJDomJar(), OrderRootType.CLASSES),
                                                       new OrderRoot(getJDomSources(), OrderRootType.SOURCES)));
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getJDomJar());
    assertSameElements(library.getFiles(OrderRootType.SOURCES), getJDomSources());
  }

  public void testJarWithSourcesInside() {
    Library library = assertOneElement(createLibraries(new OrderRoot(getJDomJar(), OrderRootType.CLASSES),
                                                       new OrderRoot(getJDomJar(), OrderRootType.SOURCES)));
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getJDomJar());
    assertSameElements(library.getFiles(OrderRootType.SOURCES), getJDomJar());
  }

  public void testTwoJarAndSources() {
    List<Library> libraries = createLibraries(new OrderRoot(getJDomJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getAsmJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getJDomSources(), OrderRootType.SOURCES));
    Library library = assertOneElement(libraries);
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getJDomJar(), getAsmJar());
    assertSameElements(library.getFiles(OrderRootType.SOURCES), getJDomSources());
  }

  public void testTwoJarWithSourcesInside() {
    List<Library> libraries = createLibraries(new OrderRoot(getJDomJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getAsmJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getJDomJar(), OrderRootType.SOURCES),
                                              new OrderRoot(getAsmJar(), OrderRootType.SOURCES));
    assertEquals(2, libraries.size());
    assertNull(libraries.get(0).getName());
    assertSameElements(libraries.get(0).getFiles(OrderRootType.CLASSES), getJDomJar());
    assertSameElements(libraries.get(0).getFiles(OrderRootType.SOURCES), getJDomJar());
    assertNull(libraries.get(1).getName());
    assertSameElements(libraries.get(1).getFiles(OrderRootType.CLASSES), getAsmJar());
    assertSameElements(libraries.get(1).getFiles(OrderRootType.SOURCES), getAsmJar());
  }

  @NotNull
  private List<Library> createLibraries(OrderRoot... roots) {
    return CreateModuleLibraryChooser.createLibrariesFromRoots(Arrays.asList(roots), myModifiableModel);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myModifiableRootModel.dispose();
    }
    finally {
      super.tearDown();
    }
  }
}
