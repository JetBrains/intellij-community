// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots.libraries;

import com.intellij.ProjectTopics;
import com.intellij.java.codeInsight.daemon.quickFix.OrderEntryTest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.libraries.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.roots.ModuleRootManagerTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 *  @author dsl
 */
public class LibraryTest extends ModuleRootManagerTestCase {
  public void testModification() {
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

    assertTrue(LibraryTableImplUtil.isValidLibrary(library));
    ApplicationManager.getApplication().runWriteAction(() -> libraryTable.removeLibrary(library));
    assertFalse(LibraryTableImplUtil.isValidLibrary(library));
  }

  public void testAddRemoveModuleLibrary() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, getJDomJar().getUrl());
    Library library = assertOneElement(OrderEntryUtil.getModuleLibraries(ModuleRootManager.getInstance(myModule)));
    assertTrue(LibraryTableImplUtil.isValidLibrary(library));
    ModuleRootModificationUtil.updateModel(myModule, model -> model.getModuleLibraryTable().removeLibrary(library));
    assertFalse(LibraryTableImplUtil.isValidLibrary(library));
  }

  public void testLibrarySerialization() {
    final long moduleModificationCount = ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests();
    Library library = PsiTestUtil.addProjectLibrary(myModule, "junit", Collections.singletonList(getJDomJar()),
                                                    Collections.singletonList(getJDomSources()));

    assertThat(ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests()).isGreaterThan(moduleModificationCount);
    Element element = serialize(library);
    String classesUrl = getJDomJar().getUrl();
    String sourcesUrl = getJDomSources().getUrl();
    assertThat(element).isEqualTo("<root>\n" +
                                  "  <library name=\"junit\">\n" +
                                  "    <CLASSES>\n" +
                                  "      <root url=\"" + classesUrl + "\" />\n" +
                                  "    </CLASSES>\n" +
                                  "    <JAVADOC />\n" +
                                  "    <SOURCES>\n" +
                                  "      <root url=\"" + sourcesUrl + "\" />\n" +
                                  "    </SOURCES>\n" +
                                  "  </library>\n" +
                                  "</root>");
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
    LibraryTable table = getProjectLibraryTable();

    Library a = createLibrary("a", null, null);
    LibraryTable.ModifiableModel model = table.getModifiableModel();
    assertSame(a, table.getLibraryByName("a"));
    assertSame(a, model.getLibraryByName("a"));
    Library.ModifiableModel libraryModel = a.getModifiableModel();
    libraryModel.setName("b");
    commit(libraryModel);

    assertNull(table.getLibraryByName("a"));
    assertNull(model.getLibraryByName("a"));
    assertSame(a, table.getLibraryByName("b"));
    assertSame(a, model.getLibraryByName("b"));
    commit(model);
    assertSame(a, table.getLibraryByName("b"));
  }

  public void testModificationCount() {
    ignoreTestUnderTreeProjectModel();

    final long moduleModificationCount = ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests();

    ProjectLibraryTableImpl table = (ProjectLibraryTableImpl)getProjectLibraryTable();
    final long projectLibraryModificationCount = table.getStateModificationCount();
    Library a = createLibrary("a", null, null);
    Library.ModifiableModel libraryModel = a.getModifiableModel();
    libraryModel.setName("b");
    commit(libraryModel);

    // module not marked as to save if project library modified, but module is not affected
    assertThat(ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests()).isEqualTo(moduleModificationCount);
    assertThat(table.getStateModificationCount()).isGreaterThan(projectLibraryModificationCount);
  }

  private static void commit(LibraryTable.ModifiableModel model) {
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
  }

  public void testFindLibraryByNameAfterChainedRename() {
    Library a = createLibrary("a", null, null);
    Library b = createLibrary("b", null, null);
    assertSame(a, getProjectLibraryTable().getLibraryByName("a"));
    assertSame(b, getProjectLibraryTable().getLibraryByName("b"));
    Library.ModifiableModel bModel = b.getModifiableModel();
    bModel.setName("c");
    commit(bModel);
    Library.ModifiableModel aModel = a.getModifiableModel();
    aModel.setName("b");
    commit(aModel);
    assertNull(getProjectLibraryTable().getLibraryByName("a"));
    assertSame(a, getProjectLibraryTable().getLibraryByName("b"));
    assertSame(b, getProjectLibraryTable().getLibraryByName("c"));
  }

  public void testReloadLibraryTable() {
    ignoreTestUnderTreeProjectModel();

    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component"));
    createLibrary("a", null, null);
    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component").addContent(new Element("library").setAttribute("name", "b")));
    assertEquals("b", assertOneElement(getProjectLibraryTable().getLibraries()).getName());
  }

  public void testReloadLibraryTableWithoutChanges() {
    ignoreTestUnderTreeProjectModel();

    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component"));
    createLibrary("a", null, null);
    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component").addContent(new Element("library").setAttribute("name", "a")));
    assertEquals("a", assertOneElement(getProjectLibraryTable().getLibraries()).getName());
  }

  public void testNonCommittedLibraryIsDisposed() {
    LibraryTable table = getProjectLibraryTable();
    LibraryTable.ModifiableModel model = table.getModifiableModel();
    Library library = model.createLibrary("a");
    model.removeLibrary(library);
    commit(model);
    assertEmpty(table.getLibraries());
  }

  public void testMergeAddRemoveChanges() {
    Library a = createLibrary("a", null, null);
    LibraryTable table = getProjectLibraryTable();

    LibraryTable.ModifiableModel model1 = table.getModifiableModel();
    model1.removeLibrary(a);

    LibraryTable.ModifiableModel model2 = table.getModifiableModel();
    model2.createLibrary("b");
    commit(model1);
    commit(model2);

    assertAllLibrariesAreNotDisposed();
    assertEquals("b", assertOneElement(table.getLibraries()).getName());
  }

  public void testMergeAddAddChanges() {
    createLibrary("a", null, null);
    LibraryTable table = getProjectLibraryTable();

    LibraryTable.ModifiableModel model1 = table.getModifiableModel();
    model1.createLibrary("b");

    LibraryTable.ModifiableModel model2 = table.getModifiableModel();
    model2.createLibrary("c");
    commit(model1);
    commit(model2);

    assertAllLibrariesAreNotDisposed();
    assertSameElements(ContainerUtil.map(table.getLibraries(), Library::getName), "a", "b", "c");
  }

  public void testMergeRemoveRemoveChanges() {
    Library a = createLibrary("a", null, null);
    Library b = createLibrary("b", null, null);
    LibraryTable table = getProjectLibraryTable();

    LibraryTable.ModifiableModel model1 = table.getModifiableModel();
    model1.removeLibrary(a);

    LibraryTable.ModifiableModel model2 = table.getModifiableModel();
    model2.removeLibrary(b);
    commit(model1);
    commit(model2);

    assertAllLibrariesAreNotDisposed();
    assertEmpty(table.getLibraries());
  }

  private void assertAllLibrariesAreNotDisposed() {
    for (Library library : getProjectLibraryTable().getLibraries()) {
      assertEmpty(library.getUrls(OrderRootType.CLASSES));
    }
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
    WriteAction.runAndWait(() -> model.commit());
  }

  public void testNativePathSerialization() {
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.compute(()-> table.createLibrary("native"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot("file://native-lib-root", NativeLibraryOrderRootType.getInstance());
    commit(model);

    Element element = serialize(library);
    assertThat(element).isEqualTo("<root>\n" +
                                  "  <library name=\"native\">\n" +
                                  "    <CLASSES />\n" +
                                  "    <JAVADOC />\n" +
                                  "    <NATIVE>\n" +
                                  "      <root url=\"file://native-lib-root\" />\n" +
                                  "    </NATIVE>\n" +
                                  "    <SOURCES />\n" +
                                  "  </library>\n" +
                                  "</root>");
  }

  @NotNull
  private LibraryTable getProjectLibraryTable() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
  }

  public void testJarDirectoriesSerialization() {
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.compute(() -> table.createLibrary("jarDirs"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addJarDirectory("file://jar-dir", false, OrderRootType.CLASSES);
    model.addJarDirectory("file://jar-dir-rec", true, OrderRootType.CLASSES);
    model.addJarDirectory("file://jar-dir-src", false, OrderRootType.SOURCES);
    commit(model);

    assertThat(serialize(library)).isEqualTo("<root>\n" +
                                             "  <library name=\"jarDirs\">\n" +
                                             "    <CLASSES>\n" +
                                             "      <root url=\"file://jar-dir\" />\n" +
                                             "      <root url=\"file://jar-dir-rec\" />\n" +
                                             "    </CLASSES>\n" +
                                             "    <JAVADOC />\n" +
                                             "    <SOURCES>\n" +
                                             "      <root url=\"file://jar-dir-src\" />\n" +
                                             "    </SOURCES>\n" +
                                             "    <jarDirectory url=\"file://jar-dir\" recursive=\"false\" />\n" +
                                             "    <jarDirectory url=\"file://jar-dir-rec\" recursive=\"true\" />\n" +
                                             "    <jarDirectory url=\"file://jar-dir-src\" recursive=\"false\" type=\"SOURCES\" />\n" +
                                             "  </library>\n" +
                                             "</root>");
  }

  private static Element serialize(Library library) {
    Element element = new Element("root");
    library.writeExternal(element);
    return element;
  }

  public void testAddRemoveJarDirectory() {
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.compute(() -> table.createLibrary("jar-directory"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addJarDirectory("file://jar-directory", false, OrderRootType.CLASSES);
    commit(model);
    assertSameElements(library.getUrls(OrderRootType.CLASSES), "file://jar-directory");

    model = library.getModifiableModel();
    model.removeRoot("file://jar-directory", OrderRootType.CLASSES);
    commit(model);
    assertEmpty(library.getUrls(OrderRootType.CLASSES));
  }

  public void testRootsMustRebuildAfterAddRemoveJarInsideJarDirectoryNonRecursive() {
    VirtualFile libDir = createChildDirectory(getOrCreateProjectBaseDir(), "myLib");
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.compute(() -> table.createLibrary("myLib"));
    Library.ModifiableModel model = library.getModifiableModel();
    VirtualFile libJar = getVirtualFile(new File(PathManagerEx.getTestDataPath() + OrderEntryTest.BASE_PATH + "lib/lib.jar"));
    copy(libJar, libDir, libJar.getName());

    model.addJarDirectory(libDir.getUrl(), false, OrderRootType.CLASSES);
    commit(model);

    ModuleRootModificationUtil.updateModel(getModule(), m -> m.addLibraryEntry(library));

    assertSize(1, library.getUrls(OrderRootType.CLASSES));
    assertSameElements(library.getUrls(OrderRootType.CLASSES), libDir.getUrl());
    assertSize(1, library.getFiles(OrderRootType.CLASSES));
    assertEquals(libDir.getPath() + "/" + libJar.getName() + "!/", library.getFiles(OrderRootType.CLASSES)[0].getPath());

    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    // wait until unlock the file?
    while (!FileUtil.delete(new File(libDir.getPath(),"lib.jar"))) {

    }
    UIUtil.dispatchAllInvocationEvents();
    libDir.refresh(false, false);
    UIUtil.dispatchAllInvocationEvents();
    assertNull(libDir.findChild("lib.jar"));

    aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNull(aClass);

    copy(libJar, libDir, libJar.getName());
    UIUtil.dispatchAllInvocationEvents();
    aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
  }

  public void testRootsMustRebuildAfterDeleteAndRestoreJar() throws IOException {
    VirtualFile libDir = getOrCreateProjectBaseDir();

    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.compute(() -> table.createLibrary("myLib"));
    Library.ModifiableModel model = library.getModifiableModel();
    VirtualFile originalLibJar = getVirtualFile(new File(PathManagerEx.getTestDataPath() + OrderEntryTest.BASE_PATH + "lib/lib.jar"));
    VirtualFile libJar = copy(originalLibJar, libDir, originalLibJar.getName());

    String libUrl = "jar://" + libJar.getPath() + "!/";
    model.addRoot(libUrl, OrderRootType.CLASSES);
    commit(model);

    ModuleRootModificationUtil.updateModel(getModule(), m -> m.addLibraryEntry(library));

    assertSize(1, library.getUrls(OrderRootType.CLASSES));
    assertSameElements(library.getUrls(OrderRootType.CLASSES), libUrl);
    assertInstanceOf(library.getFiles(OrderRootType.CLASSES)[0].getFileSystem(), JarFileSystem.class);
    assertSize(1, library.getFiles(OrderRootType.CLASSES));

    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    // wait until unlock the file?
    while (!FileUtil.delete(new File(libDir.getPath(), "lib.jar"))) {}
    UIUtil.dispatchAllInvocationEvents();
    libDir.refresh(false, false);
    UIUtil.dispatchAllInvocationEvents();
    assertNull(libDir.findChild("lib.jar"));
    
    aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNull(aClass);

    AtomicBoolean rootsChanged = new AtomicBoolean(false);
    myProject.getMessageBus().connect(myProject).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull final ModuleRootEvent event) {
        rootsChanged.set(true);
      }
    });

    FileUtil.copy(new File(originalLibJar.getPath()), new File(libDir.getPath(), originalLibJar.getName()));
    libDir.refresh(false, false);
    libJar = libDir.findFileByRelativePath(originalLibJar.getName());
    assertNotNull(libJar);

    UIUtil.dispatchAllInvocationEvents();
    aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    assertTrue(rootsChanged.get());
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
