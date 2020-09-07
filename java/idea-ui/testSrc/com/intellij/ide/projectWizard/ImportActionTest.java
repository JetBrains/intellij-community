// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ImportFromSourcesProvider;
import com.intellij.ide.util.projectWizard.ModuleImportProvider;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ImportActionTest extends ProjectWizardTestCase<AddModuleWizard> {

  public void testImportModule() {
    String path = PathManagerEx.getTestDataPath("/ide/importAction/module/foo.iml");
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    assertNotNull(file);
    assertEquals(1, ModuleManager.getInstance(getProject()).getModules().length);
    Module module = importModuleFrom(new ModuleImportProvider(), file.getPath());
    assertEquals("foo", module.getName());
    assertEquals(2, ModuleManager.getInstance(getProject()).getModules().length);
  }

  public void testImportModuleFromSources() {
    Module module = importModuleFrom(new ImportFromSourcesProvider(), PathManagerEx.getTestDataPath("/ide/importAction/sources"));
    assertEquals("importAction", module.getName());
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    assertEquals(1, roots.length);
  }

  public void testBalanced() {
    String path = getModuleMaximizationPath("balanced");
    Project project = importProjectFrom(path, null, new ImportFromSourcesProvider()).getProject();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    assertEquals(2, modules.length);
    List<VirtualFile> contentRoots = getSingleContentRoots(modules);
    assertPathsEqual(path + "/m1", contentRoots.get(0).getPath());
    assertPathsEqual(path + "/m2", contentRoots.get(1).getPath());
  }

  public void testUnbalanced() {
    String path = getModuleMaximizationPath("unbalanced");
    Project project = importProjectFrom(path, null, new ImportFromSourcesProvider()).getProject();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    assertEquals(2, modules.length);
    List<VirtualFile> contentRoots = getSingleContentRoots(modules);
    assertPathsEqual(path + "/inner/m1", contentRoots.get(0).getPath());
    assertPathsEqual(path + "/inner/m2", contentRoots.get(1).getPath());
  }

  public void testSingleModuleInProject() {
    String path = getModuleMaximizationPath("single");
    Project project = importProjectFrom(path, null, new ImportFromSourcesProvider()).getProject();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    assertEquals(1, modules.length);
    List<VirtualFile> contentRoots = getSingleContentRoots(modules);
    assertPathsEqual(path, contentRoots.get(0).getPath());
  }

  private static void assertPathsEqual(String path1, String path2) {
    assertEquals(FileUtil.normalize(path1), FileUtil.normalize(path2));
  }

  private static String getModuleMaximizationPath(String projectName) {
    String basePath = "/ide/importAction/moduleMaximization";
    return PathManagerEx.getTestDataPath(basePath + "/" + projectName);
  }

  private static List<VirtualFile> getSingleContentRoots(Module[] modules) {
    List<VirtualFile> contentRoots = new ArrayList<>();
    for (Module module : modules) {
      VirtualFile[] moduleContentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      assertEquals(1, moduleContentRoots.length);
      contentRoots.add(moduleContentRoots[0]);
    }
    return contentRoots;
  }

  public void testImportProjectFromSources() throws Exception {
    File file = createTempFile("Foo.java", "class Foo {}");
    Module module = importProjectFrom(file.getParent(), step -> {
      if (step != null) {
        assertEquals("Existing sources", myWizard.getSequence().getSelectedType());
      }
    }, new ImportFromSourcesProvider());

    VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    assertEquals(1, roots.length);
  }

  public void testImportProjectWithLibrary() throws Exception {
    File tempDirectory = createTempDirectory();
    FileUtil.copyDir(new File(PathManagerEx.getTestDataPath("/ide/importAction/project")), tempDirectory);
    Module module = importProjectFrom(tempDirectory.getPath(), null, new ImportFromSourcesProvider());
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
    Library[] libraries = table.getLibraries();
    assertEquals(1, libraries.length);
    OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    assertEquals(3, entries.length);
    assertEquals("google-play-services", entries[2].getPresentableName());
  }

  public void testProvidersCompatibility() {
    Set<Class<?>> project = ContainerUtil.map2Set(ImportModuleAction.getProviders(null), p -> p.getClass());
    assertFalse(project.contains(ModuleImportProvider.class));
    Set<Class<?>> modular = ContainerUtil.map2Set(ImportModuleAction.getProviders(getProject()), p -> p.getClass());
    assertTrue(modular.contains(ModuleImportProvider.class));
  }
}
