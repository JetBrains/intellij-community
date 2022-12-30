// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots.libraries;

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryTest;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
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
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class LibraryTest extends ModuleRootManagerTestCase {
  public void testLibrarySerialization() throws IOException {
    long moduleModificationCount = ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests();

    File projectDir = new File(myProject.getBasePath());
    File localFastUtilJar = new File(projectDir, "lib.jar");
    Path sources = getLibSources();
    File localSources = new File(projectDir, "lib-sources.zip");

    FileUtil.copy(new File(getFastUtilJar().getPath().replace("!", "")), localFastUtilJar);
    FileUtil.copy(sources.toFile(), localSources);

    PsiTestUtil.addProjectLibrary(
      myModule,
      "junit",
      Collections.singletonList(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFastUtilJar)),
      Collections.singletonList(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localSources))
    );

    assertThat(ModuleRootManagerEx.getInstanceEx(myModule).getModificationCountForTests()).isGreaterThan(moduleModificationCount);
    assertThat(serializeLibraries(myProject)).isEqualTo(
      """
        <library name="junit">
          <CLASSES>
            <root url="file://$PROJECT_DIR$/lib.jar" />
          </CLASSES>
          <JAVADOC />
          <SOURCES>
            <root url="file://$PROJECT_DIR$/lib-sources.zip" />
          </SOURCES>
        </library>"""
    );
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

  public void testNativePathSerialization() {
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.compute(() -> table.createLibrary("native"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot("file://native-lib-root", NativeLibraryOrderRootType.getInstance());
    commit(model);

    assertThat(serializeLibraries(myProject)).isEqualTo(
      """
        <library name="native">
          <CLASSES />
          <JAVADOC />
          <NATIVE>
            <root url="file://native-lib-root" />
          </NATIVE>
          <SOURCES />
        </library>"""
    );
  }

  @NotNull
  private LibraryTable getProjectLibraryTable() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
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

    ModuleRootModificationUtil.updateModel(getModule(), m -> m.removeOrderEntry(m.findLibraryOrderEntry(library)));
    aClass = JavaPsiFacade.getInstance(getProject()).findClass("l.InLib", GlobalSearchScope.allScope(getProject()));
    assertNull(aClass);
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
