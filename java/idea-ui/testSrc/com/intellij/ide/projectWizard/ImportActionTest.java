// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.util.importProject.RootsDetectionStep;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ImportFromSourcesProvider;
import com.intellij.ide.util.projectWizard.ModuleImportProvider;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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

  public void testImportProjectFromSources() throws Exception {
    File file = createTempFile("Foo.java", "class Foo {}");
    Module module = importProjectFrom(file.getParent(), step -> {
      if (step != null) {
        assertEquals("Existing Sources", myWizard.getSequence().getSelectedType());
        if (step instanceof RootsDetectionStep) {
          List<ModuleWizardStep> steps = myWizard.getSequence().getSelectedSteps();
          assertEquals(6, steps.size());
        }
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
    Set<Class> project = ContainerUtil.map2Set(ImportModuleAction.getProviders(null), p -> p.getClass());
    assertFalse(project.contains(ModuleImportProvider.class));
    Set<Class> modular = ContainerUtil.map2Set(ImportModuleAction.getProviders(getProject()), p -> p.getClass());
    assertTrue(modular.contains(ModuleImportProvider.class));
  }
}
