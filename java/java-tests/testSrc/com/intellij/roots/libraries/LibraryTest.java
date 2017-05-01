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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.NativeLibraryOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.ModuleRootManagerComponent;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.roots.ModuleRootManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.CommonProcessors;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 *  @author dsl
 */
public class LibraryTest extends ModuleRootManagerTestCase {
  public void testModification() throws Exception {
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    final Library library = WriteAction.compute(() -> libraryTable.createLibrary("NewLibrary"));
    final boolean[] listenerNotifiedOnChange = new boolean[1];
    library.getRootProvider().addRootSetChangedListener(wrapper -> listenerNotifiedOnChange[0] = true);
    final Library.ModifiableModel model1 = library.getModifiableModel();
    model1.addRoot("file://x.jar", OrderRootType.CLASSES);
    model1.addRoot("file://x-src.jar", OrderRootType.SOURCES);
    commit(model1);
    assertTrue(listenerNotifiedOnChange[0]);

    listenerNotifiedOnChange[0] = false;

    final Library.ModifiableModel model2 = library.getModifiableModel();
    model2.setName("library");
    commit(model2);
    assertFalse(listenerNotifiedOnChange[0]);

    ApplicationManager.getApplication().runWriteAction(() -> libraryTable.removeLibrary(library));
  }

  public void testLibrarySerialization() {
    final long moduleModificationCount = ((ModuleRootManagerComponent)ModuleRootManager.getInstance(myModule)).getStateModificationCount();
    Library library = PsiTestUtil.addProjectLibrary(myModule, "junit", Collections.singletonList(getJDomJar()),
                                                    Collections.singletonList(getJDomSources()));

    assertThat(((ModuleRootManagerComponent)ModuleRootManager.getInstance(myModule)).getStateModificationCount()).isGreaterThan(moduleModificationCount);
    Element element = serialize(library);
    String classesUrl = getJDomJar().getUrl();
    String sourcesUrl = getJDomSources().getUrl();
    PlatformTestUtil.assertElementEquals(
      "<root><library name=\"junit\"><CLASSES><root url=\"" + classesUrl + "\" /></CLASSES>" +
      "<JAVADOC /><SOURCES><root url=\"" + sourcesUrl + "\" /></SOURCES></library></root>",
      element);
  }

  public void testResolveDependencyToAddedLibrary() {
    final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
    model.addInvalidLibrary("jdom", LibraryTablesRegistrar.PROJECT_LEVEL);
    commit(model);
    assertEmpty(getLibraries());

    Library library = createLibrary("jdom", getJDomJar(), null);
    assertSameElements(getLibraries(), library);
  }

  public void testFindLibraryByNameAfterRename() {
    final long moduleModificationCount = ((ModuleRootManagerComponent)ModuleRootManager.getInstance(myModule)).getStateModificationCount();
    ProjectLibraryTable table = (ProjectLibraryTable)getLibraryTable();
    final long projectLibraryModificationCount = table.getStateModificationCount();
    Library a = createLibrary("a", null, null);
    LibraryTable.ModifiableModel model = table.getModifiableModel();
    assertSame(a, table.getLibraryByName("a"));
    assertSame(a, model.getLibraryByName("a"));
    Library.ModifiableModel libraryModel = a.getModifiableModel();
    libraryModel.setName("b");
    commit(libraryModel);

    // module not marked as to save if project library modified, but module is not affected
    assertThat(((ModuleRootManagerComponent)ModuleRootManager.getInstance(myModule)).getStateModificationCount()).isEqualTo(moduleModificationCount);
    assertThat(table.getStateModificationCount()).isGreaterThan(projectLibraryModificationCount);

    assertNull(table.getLibraryByName("a"));
    assertNull(model.getLibraryByName("a"));
    assertSame(a, table.getLibraryByName("b"));
    assertSame(a, model.getLibraryByName("b"));
    commit(model);
    assertSame(a, table.getLibraryByName("b"));
  }

  private static void commit(LibraryTable.ModifiableModel model) {
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
  }

  public void testFindLibraryByNameAfterChainedRename() {
    Library a = createLibrary("a", null, null);
    Library b = createLibrary("b", null, null);
    assertSame(a, getLibraryTable().getLibraryByName("a"));
    assertSame(b, getLibraryTable().getLibraryByName("b"));
    Library.ModifiableModel bModel = b.getModifiableModel();
    bModel.setName("c");
    commit(bModel);
    Library.ModifiableModel aModel = a.getModifiableModel();
    aModel.setName("b");
    commit(aModel);
    assertNull(getLibraryTable().getLibraryByName("a"));
    assertSame(a, getLibraryTable().getLibraryByName("b"));
    assertSame(b, getLibraryTable().getLibraryByName("c"));
  }

  public void testReloadLibraryTable() {
    ((LibraryTableBase)getLibraryTable()).loadState(new Element("component"));
    createLibrary("a", null, null);
    ((LibraryTableBase)getLibraryTable()).loadState(new Element("component").addContent(new Element("library").setAttribute("name", "b")));
    assertEquals("b", assertOneElement(getLibraryTable().getLibraries()).getName());
  }

  public void testResolveDependencyToRenamedLibrary() {
    Library library = createLibrary("jdom2", getJDomJar(), null);

    final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
    model.addInvalidLibrary("jdom", LibraryTablesRegistrar.PROJECT_LEVEL);
    commit(model);
    assertEmpty(getLibraries());

    Library.ModifiableModel libModel = library.getModifiableModel();
    libModel.setName("jdom");
    commit(libModel);
    assertSameElements(getLibraries(), library);
  }

  private Collection<Library> getLibraries() {
    CommonProcessors.CollectProcessor<Library> processor = new CommonProcessors.CollectProcessor<>();
    ModuleRootManager.getInstance(myModule).orderEntries().forEachLibrary(processor);
    return processor.getResults();
  }

  private static void commit(final ModifiableRootModel model) {
    new WriteAction() {
      protected void run(@NotNull final Result result) {
        model.commit();
      }
    }.execute();
  }

  public void testNativePathSerialization() {
    LibraryTable table = getLibraryTable();
    Library library = new WriteAction<Library>() {
      @Override
      protected void run(@NotNull Result<Library> result) throws Throwable {
        Library res = table.createLibrary("native");
        result.setResult(res);
      }
    }.execute().throwException().getResultObject();
    Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot("file://native-lib-root", NativeLibraryOrderRootType.getInstance());
    commit(model);

    Element element = serialize(library);
    PlatformTestUtil.assertElementEquals(
      "<root><library name=\"native\"><CLASSES /><JAVADOC />" +
      "<NATIVE><root url=\"file://native-lib-root\" /></NATIVE>" +
      "<SOURCES /></library></root>",
      element);
  }

  @NotNull
  private LibraryTable getLibraryTable() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
  }

  public void testJarDirectoriesSerialization() {
    LibraryTable table = getLibraryTable();
    Library library = WriteAction.compute(() -> table.createLibrary("jarDirs"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addJarDirectory("file://jar-dir", false, OrderRootType.CLASSES);
    model.addJarDirectory("file://jar-dir-src", false, OrderRootType.SOURCES);
    commit(model);

    Element element = serialize(library);
    PlatformTestUtil.assertElementEquals("<root>\n" +
                                         "  <library name=\"jarDirs\">\n" +
                                         "    <CLASSES>\n" +
                                         "      <root url=\"file://jar-dir\" />\n" +
                                         "    </CLASSES>\n" +
                                         "    <JAVADOC />\n" +
                                         "    <SOURCES>\n" +
                                         "      <root url=\"file://jar-dir-src\" />\n" +
                                         "    </SOURCES>\n" +
                                         "    <jarDirectory url=\"file://jar-dir\" recursive=\"false\" />\n" +
                                         "    <jarDirectory url=\"file://jar-dir-src\" recursive=\"false\" type=\"SOURCES\" />\n" +
                                         "  </library>\n" +
                                         "</root>" , element);
  }

  private static Element serialize(Library library) {
    try {
      Element element = new Element("root");
      library.writeExternal(element);
      return element;
    }
    catch (WriteExternalException e) {
      throw new AssertionError(e);
    }
  }

  public void testAddRemoveExcludedRoot() {
    VirtualFile jar = getJDomJar();
    LibraryEx library = (LibraryEx)createLibrary("junit", jar, null);
    assertEmpty(library.getExcludedRoots());

    LibraryEx.ModifiableModelEx model = library.getModifiableModel();
    model.addExcludedRoot(jar.getUrl());
    commit(model);
    assertOrderedEquals(library.getExcludedRoots(), jar);

    LibraryEx.ModifiableModelEx model2 = library.getModifiableModel();
    model2.removeExcludedRoot(jar.getUrl());
    commit(model2);
    assertEmpty(library.getExcludedRoots());
  }

  public void testRemoveExcludedRootWhenParentRootIsRemoved() {
    VirtualFile jar = getJDomJar();
    LibraryEx library = (LibraryEx)createLibrary("junit", jar, null);

    LibraryEx.ModifiableModelEx model = library.getModifiableModel();
    VirtualFile excluded = jar.findChild("org");
    assertNotNull(excluded);
    model.addExcludedRoot(excluded.getUrl());
    commit(model);

    assertOrderedEquals(library.getExcludedRoots(), excluded);
    LibraryEx.ModifiableModelEx model2 = library.getModifiableModel();
    model2.removeRoot(jar.getUrl(), OrderRootType.CLASSES);
    commit(model2);

    assertEmpty(library.getExcludedRoots());
  }

  private static void commit(final Library.ModifiableModel modifiableModel) {
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
  }
}
