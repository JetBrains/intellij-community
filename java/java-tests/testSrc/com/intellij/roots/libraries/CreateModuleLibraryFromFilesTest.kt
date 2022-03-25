// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public class CreateModuleLibraryFromFilesTest extends ModuleRootManagerTestCase {
  private LibraryTable.ModifiableModel myModifiableModel;
  private ModifiableRootModel myModifiableRootModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    myModifiableModel = myModifiableRootModel.getModuleLibraryTable().getModifiableModel();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myModifiableRootModel.dispose();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myModifiableModel = null;
      myModifiableRootModel = null;
      super.tearDown();
    }
  }

  public void testSingleJar() {
    Library library = assertOneElement(createLibraries(new OrderRoot(getFastUtilJar(), OrderRootType.CLASSES)));
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getFastUtilJar());
    assertEmpty(library.getFiles(OrderRootType.SOURCES));
  }

  public void testTwoJars() {
    List<Library> libraries = createLibraries(new OrderRoot(getFastUtilJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getAsmJar(), OrderRootType.CLASSES));
    assertEquals(2, libraries.size());
    assertNull(libraries.get(0).getName());
    assertSameElements(libraries.get(0).getFiles(OrderRootType.CLASSES), getFastUtilJar());
    assertNull(libraries.get(1).getName());
    assertSameElements(libraries.get(1).getFiles(OrderRootType.CLASSES), getAsmJar());
  }

  public void testJarAndSources() {
    Library library = assertOneElement(createLibraries(new OrderRoot(getFastUtilJar(), OrderRootType.CLASSES),
                                                       new OrderRoot(getJDomSources(), OrderRootType.SOURCES)));
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getFastUtilJar());
    assertSameElements(library.getFiles(OrderRootType.SOURCES), getJDomSources());
  }

  public void testJarWithSourcesInside() {
    Library library = assertOneElement(createLibraries(new OrderRoot(getFastUtilJar(), OrderRootType.CLASSES),
                                                       new OrderRoot(getFastUtilJar(), OrderRootType.SOURCES)));
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getFastUtilJar());
    assertSameElements(library.getFiles(OrderRootType.SOURCES), getFastUtilJar());
  }

  public void testTwoJarAndSources() {
    List<Library> libraries = createLibraries(new OrderRoot(getFastUtilJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getAsmJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getJDomSources(), OrderRootType.SOURCES));
    Library library = assertOneElement(libraries);
    assertNull(library.getName());
    assertSameElements(library.getFiles(OrderRootType.CLASSES), getFastUtilJar(), getAsmJar());
    assertSameElements(library.getFiles(OrderRootType.SOURCES), getJDomSources());
  }

  public void testTwoJarWithSourcesInside() {
    List<Library> libraries = createLibraries(new OrderRoot(getFastUtilJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getAsmJar(), OrderRootType.CLASSES),
                                              new OrderRoot(getFastUtilJar(), OrderRootType.SOURCES),
                                              new OrderRoot(getAsmJar(), OrderRootType.SOURCES));
    assertEquals(2, libraries.size());
    assertNull(libraries.get(0).getName());
    assertSameElements(libraries.get(0).getFiles(OrderRootType.CLASSES), getFastUtilJar());
    assertSameElements(libraries.get(0).getFiles(OrderRootType.SOURCES), getFastUtilJar());
    assertNull(libraries.get(1).getName());
    assertSameElements(libraries.get(1).getFiles(OrderRootType.CLASSES), getAsmJar());
    assertSameElements(libraries.get(1).getFiles(OrderRootType.SOURCES), getAsmJar());
  }

  @NotNull
  private List<Library> createLibraries(OrderRoot... roots) {
    return CreateModuleLibraryChooser.createLibrariesFromRoots(Arrays.asList(roots), myModifiableModel);
  }
}
