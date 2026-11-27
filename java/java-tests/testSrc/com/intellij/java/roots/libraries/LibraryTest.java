// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots.libraries;

import com.intellij.JavaTestUtil;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.java.codeInsight.daemon.impl.quickfix.OrderEntryTest;
import com.intellij.java.roots.ModuleRootManagerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.NativeLibraryOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyTestHelper;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.copy;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.java.roots.ModuleRootManagerFixture.moduleRootManagerFixture;
import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LibraryTest {
  private final TestFixture<ModuleRootManagerFixture> heavyFixture = moduleRootManagerFixture();

  @Test
  public void testLibrarySerialization() {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    long moduleModificationCount = ModuleRootManagerEx.getInstanceEx(fixture.getModule()).getModificationCountForTests();

    var projectDir = HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject()).toNioPath().toFile();
    var localJar = IoTestUtil.createTestJar(new File(projectDir, "lib.jar"));
    var localSources = IoTestUtil.createTestJar(new File(projectDir, "lib-sources.zip"));

    PsiTestUtil.addProjectLibrary(
      fixture.getModule(),
      "junit",
      Collections.singletonList(StandardFileSystems.local().refreshAndFindFileByPath(localJar.getAbsolutePath())),
      Collections.singletonList(StandardFileSystems.local().refreshAndFindFileByPath(localSources.getAbsolutePath()))
    );

    assertThat(ModuleRootManagerEx.getInstanceEx(fixture.getModule()).getModificationCountForTests()).isGreaterThan(moduleModificationCount);
    assertThat(serializeLibraries(fixture.getProject())).isEqualTo(
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

  @Test
  public void testFindLibraryByNameAfterChainedRename() {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    Library a = fixture.createLibrary("a", null, null);
    Library b = fixture.createLibrary("b", null, null);
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

  @Test
  public void testNativePathSerialization() {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.computeAndWait(() -> table.createLibrary("native"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot("file://native-lib-root", NativeLibraryOrderRootType.getInstance());
    commit(model);

    assertThat(serializeLibraries(fixture.getProject())).isEqualTo(
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
    return LibraryTablesRegistrar.getInstance().getLibraryTable(heavyFixture.get().getProject());
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

  @Test
  public void testRootsMustRebuildAfterAddRemoveJarInsideJarDirectoryNonRecursive() {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    VirtualFile libDir = createChildDirectory(HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject()), "myLib");
    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.computeAndWait(() -> table.createLibrary("myLib"));
    Library.ModifiableModel model = library.getModifiableModel();
    VirtualFile libJar = Objects.requireNonNull(StandardFileSystems.local().refreshAndFindFileByPath(new File(JavaTestUtil.getJavaTestDataPath() + OrderEntryTest.BASE_PATH + "lib/lib.jar").getAbsolutePath()));
    copy(libJar, libDir, libJar.getName());

    model.addJarDirectory(libDir.getUrl(), false, OrderRootType.CLASSES);
    commit(model);

    ModuleRootModificationUtil.updateModel(fixture.getModule(), m -> m.addLibraryEntry(library));
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());

    assertEquals(1, library.getUrls(OrderRootType.CLASSES).length);
    assertThat(library.getUrls(OrderRootType.CLASSES)).containsExactlyInAnyOrder(libDir.getUrl());
    assertEquals(1, library.getFiles(OrderRootType.CLASSES).length);
    assertEquals(libDir.getPath() + "/" + libJar.getName() + "!/", library.getFiles(OrderRootType.CLASSES)[0].getPath());

    PsiClass aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNotNull(aClass);

    // wait until unlock the file?
    while (!FileUtil.delete(new File(libDir.getPath(),"lib.jar"))) {

    }
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    libDir.refresh(false, false);
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
    assertNull(libDir.findChild("lib.jar"));

    aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNull(aClass);

    copy(libJar, libDir, libJar.getName());
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
    aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNotNull(aClass);

    ModuleRootModificationUtil.updateModel(fixture.getModule(), m -> m.removeOrderEntry(m.findLibraryOrderEntry(library)));
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
    aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNull(aClass);
  }

  /// See IDEA-388158
  @Test
  public void testNavigationToSourceFromJarDirectory() {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    VirtualFile libDir = createChildDirectory(HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject()), "lib");
    VirtualFile sourcesDir = createChildDirectory(libDir, "src");
    VirtualFile originalLibJar = Objects.requireNonNull(StandardFileSystems.local().refreshAndFindFileByPath(new File(JavaTestUtil.getJavaTestDataPath() + OrderEntryTest.BASE_PATH + "lib/lib.jar").getAbsolutePath()));
    VirtualFile libJar = copy(originalLibJar, libDir, originalLibJar.getName());

    File sourceRoot = new File(JavaTestUtil.getJavaTestDataPath() + OrderEntryTest.BASE_PATH + "lib/src");
    File sourceJarFile = IoTestUtil.createTestJar(new File(sourcesDir.getPath(), "lib-sources.jar"), sourceRoot);
    VirtualFile sourceJar = StandardFileSystems.local().refreshAndFindFileByPath(sourceJarFile.getAbsolutePath());
    assertNotNull(sourceJar);

    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.computeAndWait(() -> table.createLibrary("lib"));
    Library.ModifiableModel model = library.getModifiableModel();
    model.addJarDirectory(libDir.getUrl(), false, OrderRootType.CLASSES);
    model.addJarDirectory(sourcesDir.getUrl(), false, OrderRootType.SOURCES);
    commit(model);

    ModuleRootModificationUtil.updateModel(fixture.getModule(), m -> m.addLibraryEntry(library));
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());

    assertThat(library.getUrls(OrderRootType.CLASSES)).containsExactlyInAnyOrder(libDir.getUrl());
    assertThat(library.getUrls(OrderRootType.SOURCES)).containsExactlyInAnyOrder(sourcesDir.getUrl());

    VirtualFile[] classRoots = library.getFiles(OrderRootType.CLASSES);
    assertEquals(1, classRoots.length);
    assertEquals(libJar.getPath() + "!/", classRoots[0].getPath());

    VirtualFile[] sourceRoots = library.getFiles(OrderRootType.SOURCES);
    assertEquals(1, sourceRoots.length);
    assertEquals(sourceJar.getPath() + "!/", sourceRoots[0].getPath());
    VirtualFile expectedSource = sourceRoots[0].findFileByRelativePath("l/InLib.java");
    assertNotNull(expectedSource);

    PsiClass aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNotNull(aClass);
    assertInstanceOf(PsiCompiledElement.class, aClass);

    ReadAction.runBlocking(() -> {
      PsiElement navigationElement = aClass.getNavigationElement();
      assertNotSame(aClass, navigationElement);
      assertFalse(navigationElement instanceof PsiCompiledElement);
      assertEquals(expectedSource, navigationElement.getContainingFile().getVirtualFile());
    });
  }

  @Test
  public void testRootsMustRebuildAfterDeleteAndRestoreJar() throws IOException {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    VirtualFile libDir = HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject());

    LibraryTable table = getProjectLibraryTable();
    Library library = WriteAction.computeAndWait(() -> table.createLibrary("myLib"));
    Library.ModifiableModel model = library.getModifiableModel();
    VirtualFile originalLibJar = Objects.requireNonNull(StandardFileSystems.local().refreshAndFindFileByPath(new File(JavaTestUtil.getJavaTestDataPath() + OrderEntryTest.BASE_PATH + "lib/lib.jar").getAbsolutePath()));
    VirtualFile libJar = copy(originalLibJar, libDir, originalLibJar.getName());

    String libUrl = "jar://" + libJar.getPath() + "!/";
    model.addRoot(libUrl, OrderRootType.CLASSES);
    commit(model);

    ModuleRootModificationUtil.updateModel(fixture.getModule(), m -> m.addLibraryEntry(library));
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());

    assertEquals(1, library.getUrls(OrderRootType.CLASSES).length);
    assertThat(library.getUrls(OrderRootType.CLASSES)).containsExactlyInAnyOrder(libUrl);
    assertInstanceOf(JarFileSystem.class, library.getFiles(OrderRootType.CLASSES)[0].getFileSystem());
    assertEquals(1, library.getFiles(OrderRootType.CLASSES).length);

    PsiClass aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNotNull(aClass);

    // wait until unlock the file?
    while (!FileUtil.delete(new File(libDir.getPath(), "lib.jar"))) {
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    }
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    libDir.refresh(false, false);
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    assertNull(libDir.findChild("lib.jar"));
    
    aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNull(aClass);

    AtomicBoolean rootsChanged = new AtomicBoolean(false);
    fixture.getProject().getMessageBus().connect(fixture.getProject()).subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
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

    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
    aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(fixture.getProject()).findClass("l.InLib", GlobalSearchScope.allScope(fixture.getProject())));
    assertNotNull(aClass);
  }

  private static void commit(final Library.ModifiableModel modifiableModel) {
    WriteAction.runAndWait(modifiableModel::commit);
  }
}
