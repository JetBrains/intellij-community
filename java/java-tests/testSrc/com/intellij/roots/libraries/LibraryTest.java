// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots.libraries;

import com.intellij.ProjectTopics;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.java.codeInsight.daemon.quickFix.OrderEntryTest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTableImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.roots.ModuleRootManagerTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.rules.ProjectModelRule;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 *  @author dsl
 */
public class LibraryTest extends ModuleRootManagerTestCase {
  public void testLibrarySerialization() throws IOException {
    final long moduleModificationCount = ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests();

    File projectDir = new File(myProject.getBasePath());
    File localJDomJar = new File(projectDir, getJDomJar().getName());
    File localJDomSources = new File(projectDir, getJDomSources().getName());

    FileUtil.copy(new File(getJDomJar().getPath().replace("!", "")), localJDomJar);
    FileUtil.copy(new File(getJDomSources().getPath().replace("!", "")), localJDomSources);

    PsiTestUtil.addProjectLibrary(
      myModule, "junit",
      Collections.singletonList(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localJDomJar)),
      Collections.singletonList(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localJDomSources)));

    assertThat(ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests()).isGreaterThan(moduleModificationCount);
    assertThat(serializeLibraries(myProject)).isEqualTo(
      "<library name=\"junit\">\n" +
      "  <CLASSES>\n" +
      "    <root url=\"file://$PROJECT_DIR$/jdom-2.0.6.jar\" />\n" +
      "  </CLASSES>\n" +
      "  <JAVADOC />\n" +
      "  <SOURCES>\n" +
      "    <root url=\"file://$PROJECT_DIR$/jdom.zip\" />\n" +
      "  </SOURCES>\n" +
      "</library>"
    );
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
    assertSame(a, table.getLibraryByName("b"));
    commit(model);
    assertSame(a, table.getLibraryByName("b"));
  }

  public void testModificationCount() {
    ProjectModelRule.ignoreTestUnderWorkspaceModel();

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
    ProjectModelRule.ignoreTestUnderWorkspaceModel();

    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component"));
    createLibrary("a", null, null);
    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component").addContent(new Element("library").setAttribute("name", "b")));
    assertEquals("b", assertOneElement(getProjectLibraryTable().getLibraries()).getName());
  }

  public void testReloadLibraryTableWithoutChanges() {
    ProjectModelRule.ignoreTestUnderWorkspaceModel();

    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component"));
    createLibrary("a", null, null);
    ((LibraryTableBase)getProjectLibraryTable()).loadState(new Element("component").addContent(new Element("library").setAttribute("name", "a")));
    assertEquals("a", assertOneElement(getProjectLibraryTable().getLibraries()).getName());
  }

  public void testNativePathSerialization() {
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.compute(() -> table.createLibrary("native"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot("file://native-lib-root", NativeLibraryOrderRootType.getInstance());
    commit(model);

    assertThat(serializeLibraries(myProject)).isEqualTo(
      "<library name=\"native\">\n" +
      "  <CLASSES />\n" +
      "  <JAVADOC />\n" +
      "  <NATIVE>\n" +
      "    <root url=\"file://native-lib-root\" />\n" +
      "  </NATIVE>\n" +
      "  <SOURCES />\n" +
      "</library>"
    );
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

    assertThat(serializeLibraries(myProject)).isEqualTo(
      "<library name=\"jarDirs\">\n" +
      "  <CLASSES>\n" +
      "    <root url=\"file://jar-dir\" />\n" +
      "    <root url=\"file://jar-dir-rec\" />\n" +
      "  </CLASSES>\n" +
      "  <JAVADOC />\n" +
      "  <SOURCES>\n" +
      "    <root url=\"file://jar-dir-src\" />\n" +
      "  </SOURCES>\n" +
      "  <jarDirectory url=\"file://jar-dir\" recursive=\"false\" />\n" +
      "  <jarDirectory url=\"file://jar-dir-rec\" recursive=\"true\" />\n" +
      "  <jarDirectory url=\"file://jar-dir-src\" recursive=\"false\" type=\"SOURCES\" />\n" +
      "</library>"
    );
  }

  static String serializeLibraries(Project project) {
    StoreUtil.saveSettings(project);

    try {
      StringBuilder sb = new StringBuilder();
      Element root = JDOMUtil.load(new File(project.getProjectFilePath()));
      for (Element componentElement : root.getChildren("component")) {
        if ("libraryTable".equals(componentElement.getAttributeValue("name"))) {
          for (Element libraryElement : componentElement.getChildren("library")) {
            sb.append(JDOMUtil.writeElement(libraryElement));
          }
        }
      }
      return sb.toString();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
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
    while (!FileUtil.delete(new File(libDir.getPath(), "lib.jar"))) {
      UIUtil.dispatchAllInvocationEvents();
    }
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
    assertTrue(rootsChanged.get());
    libJar = libDir.findFileByRelativePath(originalLibJar.getName());
    assertNotNull(libJar);

    UIUtil.dispatchAllInvocationEvents();
    aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
  }

  private static void commit(final Library.ModifiableModel modifiableModel) {
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
  }
}
